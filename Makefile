DIRS=src
APPMAKE=Makefile.app
LIBSMAKE=Makefile.libs

include $(GOROOT)/src/Make.inc
all : app

app : libs
	export GOROOT=$(pwd):$(GOROOT)
	cd src; $(MAKE) -f $(APPMAKE) $(MFLAGS)
	mkdir build/; cp src/filetool build/
	cp src/handle_error.sh build/

libs : 
	cd src; $(MAKE) -f $(LIBSMAKE) $(MFLAGS)
	cd src; $(MAKE) -f $(LIBSMAKE) install
	mkdir -p lib; cp src/_obj/iplant/*.a lib
	rm src/_go_.6

appclean : 
	cd src; $(MAKE) -f $(APPMAKE) clean

libsclean : 
	cd src; $(MAKE) -f $(LIBSMAKE) clean
	rm $(GOROOT)/pkg/$(GOOS)_$(GOARCH)/iplant/ftutils.a

clean : appclean libsclean
	rm -rf lib
	rm -rf build




