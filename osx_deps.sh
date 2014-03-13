# Check if lein is installed
lein version >/dev/null 2>&1 || { echo >&2 "Please install leiningen before running this script."; exit 1; }
if [ "$(echo `lein version` | grep 'Leiningen \(1.\|2.0\)')" ]; then 
	echo "lein version must be 2.1 or above. Do a lein upgrade first"; exit 1;
fi

echo "### Fetching node-webkit ###"
ZIPFILE=node-webkit-v0.8.5-osx-ia32.zip
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
