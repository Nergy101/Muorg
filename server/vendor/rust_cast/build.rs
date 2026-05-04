use std::env;
extern crate protoc_rust;

use protoc_rust::Customize;

fn main() {
    let generate_proto = env::var("GENERATE_PROTO").unwrap_or_else(|_| "false".to_string());
    if generate_proto == "true" {
        protoc_rust::run(protoc_rust::Args {
            out_dir: "src/cast",
            input: &[
                "protobuf/authority_keys.proto",
                "protobuf/cast_channel.proto",
            ],
            includes: &["protobuf"],
            customize: Customize {
                ..Default::default()
            },
        }).expect("protoc");
    }

    println!("rerun-if-env-changed=GENERATE_PROTO");
    println!("rerun-if-changed=protobuf/authority_keys.proto");
    println!("rerun-if-changed=protobuf/cast_channel.proto");
}
