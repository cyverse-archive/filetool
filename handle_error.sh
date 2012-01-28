#!/bin/bash

SOURCEDIR=$1
DESTDIR=$2
JOBRETURN=$3

if [ "$JOBRETURN" -ne "0" ]
then
    PATH=/usr/local2/bin/:/usr/local/bin/:/usr/bin/ /usr/local/bin/filetool -source $SOURCEDIR -destination $DESTDIR
fi

exit $JOBRETURN

