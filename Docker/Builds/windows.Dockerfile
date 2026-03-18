# escape=`
# windows-latest on GitHub Actions = Windows Server 2022 (ltsc2022)
# https://github.com/actions/runner-images/blob/main/images/windows/WindowsServer2022-Readme.md
FROM mcr.microsoft.com/windows/server:ltsc2025

SHELL ["powershell", "-Command", "$ErrorActionPreference = 'Stop';"]

# Install Chocolatey
RUN Set-ExecutionPolicy Bypass -Scope Process -Force; `
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; `
    iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# git is needed for vcpkg clone; rustup installs the stable MSVC toolchain
RUN choco install git --yes --no-progress
RUN choco install rustup.install --yes --no-progress

# Refresh PATH so rustup/cargo are available in subsequent RUN steps
RUN $env:PATH = [System.Environment]::GetEnvironmentVariable('PATH', 'Machine') + ';' + [System.Environment]::GetEnvironmentVariable('PATH', 'User'); `
    [System.Environment]::SetEnvironmentVariable('PATH', $env:PATH, 'Machine'); `
    rustup toolchain install stable; `
    rustup default stable

# Install OpenSSL via Chocolatey (mirrors CI step 1)
RUN choco install openssl --yes --no-progress

# Install OpenSSL via vcpkg (mirrors CI step 2)
RUN git clone https://github.com/microsoft/vcpkg C:\vcpkg
RUN C:\vcpkg\bootstrap-vcpkg.bat
RUN C:\vcpkg\vcpkg.exe install openssl:x64-windows
RUN C:\vcpkg\vcpkg.exe install openssl:x64-windows-static
RUN C:\vcpkg\vcpkg.exe integrate install

ENV OPENSSL_DIR="C:\vcpkg\installed\x64-windows-static"
ENV X86_64_PC_WINDOWS_MSVC_OPENSSL_DIR="C:\vcpkg\installed\x64-windows-static"
ENV VCPKGRS_DYNAMIC=1

WORKDIR C:\build

COPY src-tauri\Cargo.toml src-tauri\Cargo.lock .\

# Minimal stub so cargo can compile dependencies without the full Tauri source
RUN New-Item -ItemType Directory -Path src | Out-Null; `
    Set-Content src\lib.rs ''

RUN cargo build --target x86_64-pc-windows-msvc 2>&1
