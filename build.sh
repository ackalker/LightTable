#!/usr/bin/env bash
echo "### Building node modules ###"
cd deploy/core
nw-npm install --target=0.8.5
cd ../..

echo "### Building cljs ###"
lein cljsbuild clean && lein cljsbuild once

echo "### Building clojure plugin ###"
cd deploy/plugins/clojure
./build.sh
