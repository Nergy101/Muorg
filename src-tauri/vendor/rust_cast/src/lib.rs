#![doc(html_root_url = "https://azasypkin.github.io/rust-cast/")]

extern crate byteorder;
#[macro_use]
extern crate log;
extern crate openssl;
extern crate protobuf;
extern crate serde;
#[macro_use]
extern crate serde_derive;
extern crate serde_json;

mod cast;
pub mod channels;
pub mod errors;
pub mod message_manager;
mod utils;

use std::borrow::Cow;
use std::net::TcpStream;
use std::rc::Rc;

use openssl::ssl::{SslConnector, SslMethod, SslStream, SslVerifyMode};

use channels::connection::{ConnectionChannel, ConnectionResponse};
use channels::heartbeat::{HeartbeatChannel, HeartbeatResponse};
use channels::media::{MediaChannel, MediaResponse};
use channels::receiver::{ReceiverChannel, ReceiverResponse};

use errors::Error;

use message_manager::{CastMessage, MessageManager};

const DEFAULT_SENDER_ID: &str = "sender-0";
const DEFAULT_RECEIVER_ID: &str = "receiver-0";

/// Supported channel message types.
pub enum ChannelMessage {
    /// Message to be processed by `ConnectionChannel`.
    Connection(ConnectionResponse),
    /// Message to be processed by `HeartbeatChannel`.
    Heartbeat(HeartbeatResponse),
    /// Message to be processed by `MediaChannel`.
    Media(MediaResponse),
    /// Message to be processed by `ReceiverChannel`.
    Receiver(ReceiverResponse),
    /// Raw message is returned when built-in channels can't process it (e.g. because of unknown
    /// `namespace`).
    Raw(CastMessage),
}

/// Structure that manages connection to a cast device.
pub struct CastDevice<'a> {
    message_manager: Rc<MessageManager<SslStream<TcpStream>>>,

    /// Channel that manages connection responses/requests.
    pub connection: ConnectionChannel<'a, SslStream<TcpStream>>,

    /// Channel that allows connection to stay alive (via ping-pong requests/responses).
    pub heartbeat: HeartbeatChannel<'a, SslStream<TcpStream>>,

    /// Channel that manages various media stuff.
    pub media: MediaChannel<'a, SslStream<TcpStream>>,

    /// Channel that manages receiving platform (e.g. Chromecast).
    pub receiver: ReceiverChannel<'a, SslStream<TcpStream>>,
}

impl<'a> CastDevice<'a> {
    /// Connects to the cast device using host name and port.
    ///
    /// # Examples
    ///
    /// ```
    /// let device = CastDevice::connect(args.flag_address.unwrap(), args.flag_port)?;
    /// ```
    ///
    /// # Arguments
    ///
    /// * `host` - Cast device host name.
    /// * `port` - Cast device port number.
    ///
    /// # Errors
    ///
    /// This method may fail if connection to Cast device can't be established for some reason
    /// (e.g. wrong host name or port).
    ///
    /// # Return value
    ///
    /// Instance of `CastDevice` that allows you to manage connection.
    pub fn connect<S>(host: S, port: u16) -> Result<CastDevice<'a>, Error>
    where
        S: Into<Cow<'a, str>>,
    {
        let host = host.into();

        debug!(
            "Establishing connection with cast device at {}:{}...",
            host, port
        );

        let connector = SslConnector::builder(SslMethod::tls())?.build();
        let tcp_stream = TcpStream::connect((host.as_ref(), port))?;

        CastDevice::connect_to_device(connector.connect(host.as_ref(), tcp_stream)?)
    }

    /// Connects to the cast device using host name and port _without_ host verification. Use on
    /// your own risk!
    ///
    /// # Examples
    ///
    /// ```
    /// let device = CastDevice::connect_without_host_verification(
    ///     args.flag_address.unwrap(), args.flag_port)?;
    /// ```
    ///
    /// # Arguments
    ///
    /// * `host` - Cast device host name.
    /// * `port` - Cast device port number.
    ///
    /// # Errors
    ///
    /// This method may fail if connection to Cast device can't be established for some reason
    /// (e.g. wrong host name or port).
    ///
    /// # Return value
    ///
    /// Instance of `CastDevice` that allows you to manage connection.
    pub fn connect_without_host_verification<S>(host: S, port: u16) -> Result<CastDevice<'a>, Error>
    where
        S: Into<Cow<'a, str>>,
    {
        let host = host.into();

        debug!(
            "Establishing non-verified connection with cast device at {}:{}...",
            host, port
        );

        let mut builder = SslConnector::builder(SslMethod::tls())?;
        builder.set_verify(SslVerifyMode::NONE);

        let connector = builder.build();
        let tcp_stream = TcpStream::connect((host.as_ref(), port))?;

        debug!(
            "Connection with {}:{} successfully established.",
            host, port
        );

        CastDevice::connect_to_device(connector.connect(host.as_ref(), tcp_stream)?)
    }

    /// Waits for any message returned by cast device (e.g. Chromecast) and returns its parsed
    /// version.
    ///
    /// # Examples
    ///
    /// ```
    /// match cast_device.receive() {
    ///     Ok(ChannelMessage::Connection(res)) => debug!("Connection message: {:?}", res),
    ///     Ok(ChannelMessage::Heartbeat(_)) => cast_device.heartbeat.pong(),
    ///     .......
    ///     Err(err) => error!("Error occurred while receiving message {}", err)
    /// }
    /// ```
    ///
    /// # Errors
    ///
    /// Usually fails if message returned by device can't be parsed.
    ///
    /// # Returned values
    ///
    /// Parsed channel message.
    /// Sets the read timeout on the underlying TCP socket so that `receive()`
    /// returns promptly when no data arrives, allowing the caller to drain a
    /// command queue between messages.  `None` restores blocking-forever behaviour.
    pub fn set_read_timeout(&self, dur: Option<std::time::Duration>) -> std::io::Result<()> {
        self.message_manager.set_read_timeout(dur)
    }

    /// Discards any messages buffered during the last `receive_find_map` call.
    /// Use this after seek/pause/play commands to prevent stale device broadcasts
    /// from being replayed in the main message loop.
    pub fn drain_message_buffer(&self) {
        self.message_manager.drain_buffer();
    }

    pub fn receive(&self) -> Result<ChannelMessage, Error> {
        let cast_message = self.message_manager.receive()?;

        if self.connection.can_handle(&cast_message) {
            return Ok(ChannelMessage::Connection(
                self.connection.parse(&cast_message)?,
            ));
        }

        if self.heartbeat.can_handle(&cast_message) {
            return Ok(ChannelMessage::Heartbeat(
                self.heartbeat.parse(&cast_message)?,
            ));
        }

        if self.media.can_handle(&cast_message) {
            return Ok(ChannelMessage::Media(self.media.parse(&cast_message)?));
        }

        if self.receiver.can_handle(&cast_message) {
            return Ok(ChannelMessage::Receiver(
                self.receiver.parse(&cast_message)?,
            ));
        }

        Ok(ChannelMessage::Raw(cast_message))
    }

    /// Connects to the cast device using provided ssl stream.
    ///
    /// # Arguments
    ///
    /// * `ssl_stream` - SSL Stream for the TCP connection established with the device.
    ///
    /// # Return value
    ///
    /// Instance of `CastDevice` that allows you to manage connection.
    fn connect_to_device(ssl_stream: SslStream<TcpStream>) -> Result<CastDevice<'a>, Error> {
        let message_manager_rc = Rc::new(MessageManager::new(ssl_stream));

        let heartbeat = HeartbeatChannel::new(
            DEFAULT_SENDER_ID,
            DEFAULT_RECEIVER_ID,
            Rc::clone(&message_manager_rc),
        );
        let connection = ConnectionChannel::new(DEFAULT_SENDER_ID, Rc::clone(&message_manager_rc));
        let receiver = ReceiverChannel::new(
            DEFAULT_SENDER_ID,
            DEFAULT_RECEIVER_ID,
            Rc::clone(&message_manager_rc),
        );
        let media = MediaChannel::new(DEFAULT_SENDER_ID, Rc::clone(&message_manager_rc));

        Ok(CastDevice {
            message_manager: message_manager_rc,
            heartbeat,
            connection,
            receiver,
            media,
        })
    }
}
