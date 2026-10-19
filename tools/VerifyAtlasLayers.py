import json
from pathlib import Path
import numpy as np
from PIL import Image
p=Path('docs/design/map-cutouts/researched-v2')
m=json.loads((p/'manifest.json').read_text());w,h=m['canvas'];seen=np.zeros((h,w),dtype=bool)
assembled=np.asarray(Image.open(p/'assembled.png').convert('RGBA'))
for item in m['layers']:
 a=np.asarray(Image.open(p/'layers'/(item['name']+'.png')).convert('RGBA'));assert a.shape==(h,w,4)
 mask=a[:,:,3]>0
 assert not np.any(mask&seen),item['name']+' overlaps another layer'
 assert np.all((a[:,:,3]==0)|(a[:,:,3]==255)),item['name']+' has stray alpha'
 assert int(mask.sum())==item['opaquePixels']
 assert np.array_equal(a[mask],assembled[mask]),item['name']+' color mismatch'
 x,y=item['offset'];cw,ch=item['size'];crop=np.asarray(Image.open(p/'cropped'/(item['name']+'.png')).convert('RGBA'))
 assert np.array_equal(crop,a[y:y+ch,x:x+cw]),item['name']+' cropped placement mismatch'
 seen|=mask
 assert not np.any(mask[0]) and not np.any(mask[-1]) and not np.any(mask[:,0]) and not np.any(mask[:,-1])
 print(item['name']+': alpha, alignment, pixels, crop and overlap verified')
assert np.array_equal(seen,assembled[:,:,3]>0)
(p/'verification.txt').write_text('PASS: seven 3840x3840 PNG layers.\nPASS: binary transparent alpha and no layer touches the canvas edge.\nPASS: zero pairwise pixel overlap; union equals assembled image.\nPASS: layer colors equal assembled terrain; cropped layers equal the manifest-positioned pixels.\nHistorical precision remains limited by the documented reconstruction.\n')
print('All seven layers reconstruct the master exactly.')
