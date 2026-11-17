"""Convert the approved Figma SVGs and motion export to the native XML scene.

Usage: python tools/Export-StartupScene.py <export-directory>
Input: assets.json + downloaded SVGs, motion.json, shapes.json, geometry.json.
Figma: cJ3hR7S74FoVuGDjqQOkcI / 1:5. No network access or design mutations.
"""
import json
import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

source = Path(sys.argv[1])
target = Path(__file__).resolve().parents[1] / 'app/src/main/res/xml/startup_scene.xml'
read = lambda name: json.loads((source / name).read_text(encoding='utf-8'))
motion = {n['nodeId']: n for n in read('motion.json')['nodes']}
geometry = {n['id']: n for n in read('geometry.json')}
scene = ET.Element('scene', source='cJ3hR7S74FoVuGDjqQOkcI/1:5', duration='1100')

def tracks(parent, node_id):
    css = motion[node_id]['codeSnippets']['css']
    for block in css.split('@keyframes ')[1:]:
        frames = list(re.finditer(r'([\d.]+)%\s*\{([^}]+)\}', block))
        prop = re.search(r'([\w-]+):\s*([^;]+);', frames[0][2])[1]
        if prop == 'animation-timing-function':
            prop = re.findall(r'([\w-]+):\s*([^;]+);', frames[0][2])[-1][0]
        track = ET.SubElement(parent, 'track', property=prop)
        for f in frames:
            values = dict(re.findall(r'([\w-]+):\s*([^;]+);', f[2]))
            ET.SubElement(track, 'key', time=str(round(float(f[1])*11, 5)),
                          value=values[prop].removesuffix('px'),
                          easing=values.get('animation-timing-function', 'linear'))

def paths(parent, asset):
    root = ET.parse(source / (asset + '.svg')).getroot()
    for path in root.iter('{http://www.w3.org/2000/svg}path'):
        assert 'transform' not in path.attrib
        ET.SubElement(parent, 'path', data=path.attrib['d'],
                      stroke=path.get('stroke-width', '0'), color='#FFFFFF')

for shape in read('shapes.json'):
    g = geometry[shape['id']]
    m = g['transform']
    cx = m[0][0]*g['width']/2 + m[0][1]*g['height']/2 + m[0][2]
    cy = m[1][0]*g['width']/2 + m[1][1]*g['height']/2 + m[1][2]
    node = ET.SubElement(scene, 'decoration', id=shape['id'], cx=str(cx), cy=str(cy))
    for child in shape['children']:
        c = child['fills'][0]['color']
        color = '#' + ''.join(f'{round(c[k]*255):02X}' for k in ('r','g','b'))
        m = child['transform']
        for p in child['paths']:
            ET.SubElement(node, 'path', data=p['data'], color=color, stroke='0',
                          matrix=','.join(str(v) for row in m for v in row))
    tracks(node, shape['id'])
front = ET.SubElement(scene, 'arrows')
tracks(front, '8:7')
assets = read('assets.json')
members = list(motion)[6:36]
assert len(members) == 30
for a, node_id in zip(assets[:30], members):
    frame = ET.SubElement(front, 'frame', id=node_id)
    paths(frame, a['name'])
    tracks(frame, node_id)
ring = ET.SubElement(scene, 'ring', radius=str(geometry['8:81']['width']/2))
tracks(ring, '8:81')
kit = ET.SubElement(scene, 'kit', x=str(geometry['8:98']['transform'][0][2]),
                    y=str(geometry['8:98']['transform'][1][2]))
paths(kit, assets[-1]['name'])
tracks(kit, '8:97')
ET.indent(scene)
target.parent.mkdir(parents=True, exist_ok=True)
ET.ElementTree(scene).write(target, encoding='utf-8', xml_declaration=True)
print(f'{target}: {target.stat().st_size} bytes, {len(members)} arrow frames')
