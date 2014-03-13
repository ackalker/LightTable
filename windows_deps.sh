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
cp `cygpath --unix $(node -e 'console.log(process.execPath)')` deploy/plugins/node/node.exe

# Check if curl is installed
curl --version >/dev/null 2>&1 || { echo >&2 "Please install curl before running this script."; exit 1; }

echo "### Fetching node-webkit ###"
ZIPFILE=node-webkit-v0.8.5-win-ia32.zip
curl -O https://s3.amazonaws.com/node-webkit/v0.8.5/$ZIPFILE
cd deploy
unzip ../$ZIPFILE
cd ..
rm $ZIPFILE
chmod u+rwx deploy

echo "### Fetching clojure plugin ###"
mkdir -p deploy/plugins
cd deploy/plugins
rm -rf clojure
git clone https://github.com/LightTable/Clojure.git clojure
