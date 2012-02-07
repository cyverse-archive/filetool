Summary: iPlant File Tool
Name: iplant-filetool2
Version: 0.0.1
Release: 5
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
GOROOT=/home/tomcat/go gb


%install
install -m755 bin/filetool $RPM_BUILD_ROOT/usr/local/bin/

%clean
GOROOT=/home/tomcat/go gb -c

%files
%defattr(0764,condor,condor)
%attr(0775, condor,condor) /usr/local/bin/filetool
