#!/bin/bash

for I in 1 3 6 9 ; do cat xml | zstd --single-thread --no-check -$I -o xml-$I.zst -f  ; done

cat ./xml-1.zst ./xml-1.zst > ./xml-1x2.zst
