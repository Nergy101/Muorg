; Muorg NSIS installer hooks
; Adds / removes a Windows Firewall rule so the release binary can
; open mDNS multicast sockets and accept cast streaming connections.

!macro customInstall
  ; Add inbound firewall rule for Muorg
  nsExec::ExecToLog 'netsh advfirewall firewall add rule \
    name="Muorg" \
    dir=in \
    action=allow \
    program="$INSTDIR\muorg.exe" \
    enable=yes \
    profile=any \
    description="Allow Muorg to discover and stream to cast devices on the local network"'
!macroend

!macro customUnInstall
  ; Remove the firewall rule on uninstall
  nsExec::ExecToLog 'netsh advfirewall firewall delete rule name="Muorg" program="$INSTDIR\muorg.exe"'
!macroend
