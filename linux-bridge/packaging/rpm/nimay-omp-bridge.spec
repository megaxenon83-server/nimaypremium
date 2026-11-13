Name:           nimay-omp-bridge
Version:        1.0.1
Release:        1%{?dist}
Summary:        Nimay OMP Bridge â€” Linux
License:        MIT
URL:            https://chat.nimay.net/nimayopp/
Source0:        %{name}-%{version}.tar.gz

Requires:       nodejs >= 18, bash, coreutils
BuildArch:      noarch

%description
Nimay OMP Bridge â€” Linux bridge for Android client pairing on port 7802.
Installs at /opt/nimay-omp-bridge and registers a systemd service that
starts automatically on boot (no login required). Pair the Android app
via the pairing token shown after installation.

%prep
%autosetup -n %{name}-%{version}

%build
# No build step â€” bridge is pure JavaScript

%install
rm -rf %{buildroot}
mkdir -p %{buildroot}/opt/nimay-omp-bridge/scripts
mkdir -p %{buildroot}/opt/nimay-omp-bridge/tools
mkdir -p %{buildroot}/opt/nimay-omp-bridge/voice
mkdir -p %{buildroot}/usr/lib/systemd/system

# Install bridge + scripts + tools + voice (files are at source root)
cp bridge/omp-bridge.mjs %{buildroot}/opt/nimay-omp-bridge/
cp bridge/config.template.json %{buildroot}/opt/nimay-omp-bridge/
cp scripts/configure.sh %{buildroot}/opt/nimay-omp-bridge/scripts/
cp scripts/write-omp-bin.sh %{buildroot}/opt/nimay-omp-bridge/scripts/
cp -r tools/* %{buildroot}/opt/nimay-omp-bridge/tools/ 2>/dev/null || true
cp -r voice/* %{buildroot}/opt/nimay-omp-bridge/voice/ 2>/dev/null || true

# Systemd unit files (nimay-omp-bridge.service is at source root)
cp nimay-omp-bridge.service %{buildroot}/opt/nimay-omp-bridge/
cp nimay-omp-bridge.service %{buildroot}/usr/lib/systemd/system/nimay-omp-bridge.service

%post
/bin/bash /opt/nimay-omp-bridge/scripts/configure.sh --port 7802 || true

%preun
systemctl stop nimay-omp-bridge 2>/dev/null || true
systemctl disable nimay-omp-bridge 2>/dev/null || true

%postun
systemctl daemon-reload || true

%files
/opt/nimay-omp-bridge/omp-bridge.mjs
/opt/nimay-omp-bridge/config.template.json
/opt/nimay-omp-bridge/scripts/configure.sh
/opt/nimay-omp-bridge/scripts/write-omp-bin.sh
/opt/nimay-omp-bridge/tools/
/opt/nimay-omp-bridge/voice/
/opt/nimay-omp-bridge/nimay-omp-bridge.service
/usr/lib/systemd/system/nimay-omp-bridge.service

%changelog
* Sun Nov 10 2024 Nimay OMP <nimay@nimay.net> - 1.0.0-1
- Initial release
