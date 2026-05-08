import xml.etree.ElementTree as ET

tree = ET.parse('app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml')
root = tree.getroot()

exclusions = ["ui", "com/adamoutler/ssh/ui"]

results = []
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
                missed = int(counter.get('missed'))
                covered = int(counter.get('covered'))
                if missed > 0:
                    results.append((missed, covered, f"{pkg_name}.{cls_name}"))

results.sort(key=lambda x: x[0], reverse=True)
for missed, covered, name in results[:20]:
    total = missed + covered
    pct = 100.0 * covered / total if total > 0 else 0
    print(f"{name}: {missed} missed, {pct:.1f}% covered")
