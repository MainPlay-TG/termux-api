import sys
from MainShortcuts2 import ms
from traceback import print_exception
from xml.etree import ElementTree
KEY_NAME = "{http://schemas.android.com/apk/res/android}name"
MANIFEST_PATH = ms.MAIN_DIR + "/app/src/main/AndroidManifest.xml"
exist_perms: set[str] = set()
main_et = ElementTree.parse(MANIFEST_PATH)
new_perms: set[str] = set()
ElementTree.register_namespace("android", "http://schemas.android.com/apk/res/android")


def import_from_xml(path: ElementTree.ElementTree | str):
  if isinstance(path, ElementTree.ElementTree):
    et = path.getroot()
  else:
    et = ElementTree.parse(path)
  for perm in et.findall("uses-permission"):
    name: str = perm.get(KEY_NAME)
    if name:
      if name.startswith("android.permission."):
        yield name
    else:
      print(list(perm.keys()))


for name in import_from_xml(main_et):
  exist_perms.add(name)
for file in sys.argv[1:]:
  try:
    for name in import_from_xml(file):
      new_perms.add(name)
  except Exception as exc:
    print("Ошибка при чтении файла", file)
    print_exception(exc)
main_root = main_et.getroot()
edited = False
for name in new_perms:
  if not name in exist_perms:
    print("Новое разрешение:", name)
    el = ElementTree.Element("uses-permission", {KEY_NAME: name, "android:required": "false"})
    main_root.append(el)
    edited = True
if edited:
  main_et.write(MANIFEST_PATH, "utf-8", True)
