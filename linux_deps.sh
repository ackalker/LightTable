#!/usr/bin/env bash
# Check if lein is installed
lein version >/dev/null 2>&1 || { echo >&2 "Please install leiningen before running this script."; exit 1; }
if [ "$(echo `lein version` | grep 'Leiningen \(1.\|2.0\)')" ]; then 
	echo "lein version must be 2.1 or above. Do a lein upgrade first"; exit 1;
fi

# Check if node is installed
node --version >/dev/null 2>&1 || { echo >&2 "Please install NodeJS before running this script."; exit 1; }
if ! [ "$(echo `node --version` | grep 'v0.1[012]')" ]; then 
	echo "node version must be 0.10 or above. Update NodeJS first"; exit 1;
fi

mkdir -p deploy/plugins/node
ln -sf $(which node) deploy/plugins/node/node

which curl &> /dev/null
if [ $? -ne 0 ]; then
	echo "Please install curl before running this."
	exit
fi

echo "### Fetching node-webkit ###"
ARCH=""
if [ $(getconf LONG_BIT) == "64" ]; then ARCH="x64"; else ARCH="ia32"; fi
TARBALL=node-webkit-v0.8.5-linux-$ARCH.tar.gz
curl -O https://s3.amazonaws.com/node-webkit/v0.8.5/$TARBALL
tar -xzf $TARBALL
rm $TARBALL
cp -ar ${TARBALL%.tar.gz}/* deploy
rm -rf ${TARBALL%.tar.gz}

echo "### Fetching clojure plugin ###"
mkdir -p deploy/plugins
cd deploy/plugins
rm -rf clojure
git clone https://github.com/LightTable/Clojure.git clojure
