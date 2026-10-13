#!/usr/bin/env bash
# Сборка RHVoice из исходников на GitHub (без apt и без интернета у Android-приложения).
# Нужны: gcc/g++, make, python3 (+ pip install cmake), curl, unzip, git.
# Результат: $RHV_DIR/build/src/lib/libRHVoice.so и $RHV_DIR/share/RHVoice (voices/, languages/).
# Патчи сборки делаются только в копии $RHV_DIR (наш репозиторий не меняется):
#  - sonic/sanitizers/cldr/голоса/русский язык скачиваются архивами codeload.github.com;
#  - voice.cpp: boost::algorithm заменён std (boost для одной строки не ставим);
#  - собираются только три русских голоса; утилиты с tclap (make-hts-labels) не нужны.
set -euo pipefail
RHV_DIR="${RHV_DIR:-/tmp/rhv}"
CMAKE="${CMAKE:-cmake}"
mkdir -p "$RHV_DIR" && cd "$RHV_DIR"

dl() { curl -sfL -m 600 -o "$2" "https://codeload.github.com/$1/zip/refs/heads/$3"; }
dl RHVoice/RHVoice RHVoice.zip master && rm -rf RHVoice-master && unzip -q RHVoice.zip
cd RHVoice-master
dl waywardgeek/sonic sonic.zip master && unzip -q sonic.zip && rm -rf external/libs/sonic && mv sonic-master external/libs/sonic
dl unicode-org/cldr cldr.zip main && unzip -q cldr.zip && rm -rf src/third-party/cldr && mv cldr-main src/third-party/cldr
dl arsenm/sanitizers-cmake san.zip master && unzip -q san.zip && rm -rf cmake/thirdParty/sanitizers && mv sanitizers-cmake-master cmake/thirdParty/sanitizers
for v in aleksandr-rus irina-rus elena-rus; do dl RHVoice/$v $v.zip main && rm -rf data/voices/$v && unzip -q $v.zip && mv $v-main data/voices/$v; done
dl RHVoice/Russian ru.zip main && rm -rf data/languages/Russian && unzip -q ru.zip && mv Russian-main data/languages/Russian
dl RHVoice/dicts dicts.zip master && unzip -q dicts.zip && cp -r dicts-master/* data/languages/Russian/

# Оставить только три русских голоса (остальные каталоги без языка ломают CMake).
cd data/voices && for d in */; do case "$d" in aleksandr-rus/|irina-rus/|elena-rus/|CMakeLists.txt) ;; *) rm -rf "$d";; esac; done; cd ../..
sed -i 's|^add_subdirectory("${thirdPartyCMakeModulesDir}/CCache")|# (ccache disabled)|' CMakeLists.txt
python3 - <<'PY'
p='src/core/voice.cpp'
s=open(p,encoding='utf-8').read()
s=s.replace('#include "boost/algorithm/string.hpp"','#include <algorithm>\n#include <cctype>')
s=s.replace('id=boost::replace_all_copy(boost::to_lower_copy(name.get(), std::locale::classic()), "-", "_");',
 '{ std::string t=name.get(); for(auto& ch: t){ ch=(char)std::tolower((unsigned char)ch); if(ch==\'-\') ch=\'_\'; } id=t; }')
open(p,'w',encoding='utf-8').write(s)
PY
# Версия берётся из git-тега.
git init -q && git add -A >/dev/null 2>&1 && git -c user.email=b@b -c user.name=b commit -qm build && git tag 1.18.4

$CMAKE -S . -B build -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$RHV_DIR/install" \
  -DVOICE_aleksandr-rus_LANG=ru -DVOICE_irina-rus_LANG=ru -DVOICE_elena-rus_LANG=ru
$CMAKE --build build -j8 --target RHVoice RHVoice_audio RHVoice_core RHVoice_language_ru \
  RHVoice_voice_ru_aleksandr_rus RHVoice_voice_ru_irina_rus RHVoice_voice_ru_elena_rus

# Данные в формате, который ищет движок: $RHV_DIR/share/RHVoice/{voices,languages}
mkdir -p "$RHV_DIR/share/RHVoice/voices" "$RHV_DIR/share/RHVoice/languages"
for v in aleksandr-rus irina-rus elena-rus; do cp -r "data/voices/$v" "$RHV_DIR/share/RHVoice/voices/"; done
cp -r data/languages/Russian "$RHV_DIR/share/RHVoice/languages/"
echo "RHVoice собран: $RHV_DIR/build/src/lib/libRHVoice.so"
echo "Запуск: LD_LIBRARY_PATH=$RHV_DIR/build/src/core:$RHV_DIR/build/src/audio:$RHV_DIR/build/src/lib RHVOICE_LIB=$RHV_DIR/build/src/lib/libRHVoice.so RHVOICE_DATA=$RHV_DIR/share/RHVoice python3 scripts/gen_voice.py --engine rhvoice"
