import xml.etree.ElementTree as ET
tree = ET.parse('app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml')
root = tree.getroot()
results = []
for pkg in root.findall('package'):
    for cls in pkg.findall('class'):
        for counter in cls.findall('counter'):
            if counter.get('type') == 'INSTRUCTION':
                missed = int(counter.get('missed'))
                covered = int(counter.get('covered'))
                total = missed + covered
                if missed > 20:
                    results.append((missed, f"{pkg.get('name')}.{cls.get('name')}: {missed} missed, {covered} covered ({100*covered/total if total > 0 else 0:.1f}%)"))
results.sort(reverse=True, key=lambda x: x[0])
for r in results:
    print(r[1])
