#!/bin/bash

for I in 1 3 6 9 ; do cat xml | zstd --single-thread --no-check -$I -o xml-$I.zst -f  ; done

cat ./xml-1.zst ./xml-1.zst > ./xml-1x2.zst

# advanced compression check file
zstd --single-thread --no-check -o xml-advanced.zst -f --zstd=wlog=23,slog=4,tlen=32,mml=7,strat=7,hlog=16,clog=15 < xml

# generate compressed file with pledged size set (not using streaming api)
zstd xml --single-thread --no-check -1 -o xml-1-sized.zst -f
cat ./xml-1-sized.zst ./xml-1-sized.zst > ./xml-1-sizedx2.zst