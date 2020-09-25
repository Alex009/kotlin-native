#! /bin/bash

# This script scans Xcode directores for unknown frameworks
# and tries to add them as Kotlin/Native def files.
# Note that some frameworks are unsupported (e.g. swift-only) and marked as .disabled.
# Others may be supported and manual adjustment required (.attention_required).
#
# jq is required to run the script. It can be installed via `brew install jq`.
#
# Args:
# 1. Platform name: ios, tvos, osx or watchos
# 2. Path to Kotlin/Native sources.

NATIVE_SRCDIR=$2

case $1 in
tvos*)
  DEV_SDK=$(xcrun --show-sdk-path --sdk appletvos)
  SIM_SDK=$(xcrun --show-sdk-path --sdk appletvsimulator)
  OS_NAME="tvOS"
  ;;
ios*)
  DEV_SDK=$(xcrun --show-sdk-path --sdk iphoneos)
  SIM_SDK=$(xcrun --show-sdk-path --sdk iphonesimulator)
  OS_NAME="iOS"
  ;;
watchos*)
  DEV_SDK=$(xcrun --show-sdk-path --sdk watchos)
  SIM_SDK=$(xcrun --show-sdk-path --sdk watchsimulator)
  OS_NAME="watchOS"
  ;;
osx*)
  DEV_SDK=$(xcrun --show-sdk-path)
  SIM_SDK=$DEV_SDK
  OS_NAME="macOS"
  ;;
*)
  echo "Expected one of: osx ios watchos tvos. Got: $1"
  exit 1
esac

DEFS=$NATIVE_SRCDIR/platformLibs/src/platform/$1
FRAMEWORKS_DEV=$DEV_SDK/System/Library/Frameworks
FRAMEWORKS_SIM=$SIM_SDK/System/Library/Frameworks

DEFS_FILE=$(mktemp)
FRAMEWORKS_DEV_FILE=$(mktemp)
FRAMEWORKS_SIM_FILE=$(mktemp)
FRAMEWORKS_COMMON_FILE=$(mktemp)
ls $DEFS | grep .def | cut -d '.' -f 1 > $DEFS_FILE
ls $FRAMEWORKS_DEV | grep .framework | cut -d '.' -f 1 > $FRAMEWORKS_DEV_FILE
ls $FRAMEWORKS_SIM | grep .framework | cut -d '.' -f 1 > $FRAMEWORKS_SIM_FILE

# Some frameworks might be available for device but not for framework, and vice versa.
# Platform library should contain the common part.
comm -12 $FRAMEWORKS_DEV_FILE $FRAMEWORKS_SIM_FILE > $FRAMEWORKS_COMMON_FILE
ABSENT=$(comm -13 $DEFS_FILE $FRAMEWORKS_COMMON_FILE)

rm $DEFS_FILE
rm $FRAMEWORKS_DEV_FILE
rm $FRAMEWORKS_SIM_FILE
rm $FRAMEWORKS_COMMON_FILE

AVAILABLE=()
UNAVAILABLE=()
SWIFT_ONLY=()
DRIVER_KIT=()
OS_UNSUPPORTED=()
JSON=$(mktemp)

# Try to read information about framework on developer.apple.com
function check_is_objc {
    FRAMEWORK_NAME=$1
    URL=https://developer.apple.com/tutorials/data/documentation
    # Try to force Objective-C documentation. Swift is used by default.
    STATUS=$(curl -s -o $JSON -w "%{http_code}" $URL/$FRAMEWORK_NAME.json?language=objc)
    # Some old frameworks don't have a documentation page.
    if [[ $STATUS -ne 200 ]]
    then
        UNAVAILABLE+=($FRAMEWORK_NAME)
        return
    fi
    # DriverKit is C++. Drop it.
    if [[ $(cat $JSON | jq '.metadata.platforms[] | .name' | grep DriverKit) ]]
    then
        DRIVER_KIT+=($FRAMEWORK_NAME)
        return
    fi
    if [[ ! $(cat $JSON | jq '.metadata.platforms[] | .name' | grep $OS_NAME) ]]
    then
        OS_UNSUPPORTED+=($FRAMEWORK_NAME)
        return
    fi
    LANG=$(cat $JSON | jq '.identifier.interfaceLanguage' | grep swift)
    if [[ $LANG = \"swift\" ]]
    then
        SWIFT_ONLY+=($FRAMEWORK_NAME)
        return
    fi
    AVAILABLE+=($FRAMEWORK_NAME)
}

for framework in $ABSENT
do
    check_is_objc $framework
done

rm $JSON

PLATFORM_LIBS=$NATIVE_SRCDIR/platformLibs/src/platform/$1

function create_def_content {
    FRAMEWORK=$1
    DEF_FILE=$2
    echo "language = Objective-C" >> $DEF_FILE
    echo "package = platform.$FRAMEWORK" >> $DEF_FILE
    echo "modules = $FRAMEWORK" >> $DEF_FILE
    echo "compilerOpts = -framework $FRAMEWORK" >> $DEF_FILE
    echo "linkerOpts = -framework $FRAMEWORK" >> $DEF_FILE
}

function create_def {
    FRAMEWORK=$1
    DEF_FILE=$PLATFORM_LIBS/$FRAMEWORK.def
    touch $DEF_FILE
    create_def_content $FRAMEWORK $DEF_FILE
    echo "Created $DEF_FILE"
}

function create_disabled {
    FRAMEWORK=$1
    REASON=$2
    EXTENSION=$3
    DEF_FILE=$PLATFORM_LIBS/$FRAMEWORK.def.$EXTENSION
    touch $DEF_FILE
    create_def_content $FRAMEWORK $DEF_FILE
    echo "#Disabled: $REASON" >> $DEF_FILE
    echo "Created $DEF_FILE"
}

if [ ${#AVAILABLE[@]} -ne 0 ]
then
    echo "New frameworks added."
fi
for framework in ${AVAILABLE[*]}
do
    if [[ -d $DEV_SDK/System/Library/Frameworks/$framework.framework/Modules ]]
    then
        create_def $framework
    else
        create_disabled $framework "Framework without module" attention_required
    fi
done

if [ ${#UNAVAILABLE[@]} -ne 0 ]
then
    echo "Documentation for the following frameworks is not directly accessible."
    echo "They may be available by different name."
    echo "For example, AppClip is accessed as app_clips ¯\_(ツ)_/¯."
fi
for framework in ${UNAVAILABLE[*]}
do
    create_disabled $framework "Unavailable" attention_required
done

if [ ${#SWIFT_ONLY[@]} -ne 0 ]
then
    echo "The following frameworks doesn't provide Objective-C API."
fi
for framework in ${SWIFT_ONLY[*]}
do
    create_disabled $framework "Swift-only framework" disabled
done

if [ ${#DRIVER_KIT[@]} -ne 0 ]
then
    echo "The following frameworks are from DriverKit."
fi
for framework in ${DRIVER_KIT[*]}
do
    create_disabled $framework "part of DriverKit" disabled
done

if [ ${#OS_UNSUPPORTED[@]} -ne 0 ]
then
    echo "The following frameworks are not officially provided for $1."
fi
for framework in ${OS_UNSUPPORTED[*]}
do
    create_disabled $framework "Not officially available for $1" disabled
done