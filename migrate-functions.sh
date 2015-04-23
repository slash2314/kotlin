#!/bin/bash

# Note: works only for BSD / Mac OS X. For Linux replace '' in sed arguments to -e

set -e

git grep --files-with-matches 'import kotlin.Function' -- '*.java' | xargs sed -i '' 's/^import kotlin.Function..*;$/import kotlin.*;\
import kotlin.jvm.functions.*;/g'
