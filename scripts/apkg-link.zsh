#!/usr/bin/env zsh
# Makes a medslave deck link from an .apkg's download link: opening it on a phone with medslave
# downloads and imports the deck (the app refuses anything that isn't an Anki package).
#
#   scripts/apkg-link.zsh <url>        check the url gives a zip, then print the link
#   scripts/apkg-link.zsh -n <url>     skip the check
#
# Google Drive and Dropbox share links work as they are (the app makes them direct).
emulate -L zsh
setopt err_return no_unset pipe_fail

check=1
if [[ ${1-} == -n ]]; then check=0; shift; fi
if (( $# != 1 )); then
  print -u2 "usage: ${0:t} [-n] <url of an .apkg>"
  return 2 2>/dev/null || exit 2
fi
url=$1
if [[ ${url:l} != http://* && ${url:l} != https://* ]]; then
  print -u2 "not a link: $url (it must start with https://)"
  exit 1
fi

if (( check )); then
  # the same test the app makes: a zip starts with PK\3\4 (Drive/Dropbox share pages are checked by the app itself)
  if [[ $url != *drive.google.com* && $url != *dropbox.com* ]]; then
    head=$(curl -sfL --max-time 30 -A medslave "$url" 2>/dev/null | head -c 4 | xxd -p) || true
    if [[ $head != 504b0304 ]]; then
      print -u2 "that link doesn't download an .apkg (got ${head:-nothing}); use a direct download link, or -n to skip this check"
      exit 1
    fi
  fi
fi

# percent-encode everything but unreserved characters (RFC 3986)
enc=''
local LC_ALL=C c
for (( i = 1; i <= ${#url}; i++ )); do
  c=${url[i]}
  if [[ $c == [A-Za-z0-9._~-] ]]; then enc+=$c; else enc+=$(printf '%%%02X' "'$c"); fi
done
print -r -- "medslave://import?url=$enc"
