#!/bin/bash

for I in 1 3 6 9 ; do cat xml | zstd --single-thread --no-check -$I -o xml-$I.zst -f  ; done

cat ./xml-1.zst ./xml-1.zst > ./xml-1x2.zst

# advanced compression check file
zstd --single-thread --no-check -o xml-advanced.zst -f --zstd=wlog=23,slog=4,tlen=32,mml=7,strat=7,hlog=16,clog=15 < xml
