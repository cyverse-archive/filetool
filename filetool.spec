Summary: iPlant File Tool
Name: iplant-filetool2
Version: 0.0.1
Release: 4
Epoch: 0
Group: Applications
BuildRoot: %{_tmppath}/%{name}-%{version}-buildroot
License: BSD
Provides: iplant-filetool2
Source0: %{name}-%{version}.tar.gz

%description
iPlant File Tool

%prep
%setup -q
mkdir -p $RPM_BUILD_ROOT/usr/local/bin

%build
gb


%install
install -m755 _bin/filetool $RPM_BUILD_ROOT/usr/local/bin/
install -m755 handle_error.sh $RPM_BUILD_ROOT/usr/local/bin/

%clean
gb -c

%files
%defattr(0764,condor,condor)
%attr(0775, condor,condor) /usr/local/bin/filetool
%attr(0755, condor,condor) /usr/local/bin/handle_error.sh

