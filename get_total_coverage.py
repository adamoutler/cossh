import xml.etree.ElementTree as ET
tree = ET.parse('app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml')
root = tree.getroot()
exclusions = ["ui", "com/adamoutler/ssh/ui"]
total_missed = 0
total_covered = 0
for pkg in root.findall('package'):
    pkg_name = pkg.get('name')
    if any(ex in pkg_name for ex in exclusions):
        continue
    for cls in pkg.findall('class'):
        cls_name = cls.get('name')
        if "PasswordCipher" in cls_name or "SecureCrashHandler" in cls_name:
            continue
        for counter in cls.findall('counter'):
            if counter.get('type') == 'INSTRUCTION':
                total_missed += int(counter.get('missed'))
                total_covered += int(counter.get('covered'))

total = total_missed + total_covered
if total > 0:
    print(f"Total Coverage: {100.0 * total_covered / total:.2f}% ({total_covered}/{total})")
