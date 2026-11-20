"""Offscreen wing depth regression using Minecraft 1.21.1's actual shaders.

Optional development check, independent of the standard Java/Gradle rule suite.
Run from the repository root after preparing the NeoForge client artifacts:
    python -m pip install moderngl
    python tools/check_wing_depth.py
Requires an EGL-capable OpenGL implementation (headless Mesa works).
This checks shader/depth/compositing behavior, rather than launching Minecraft.
"""
from pathlib import Path
from array import array
import zipfile,re
import moderngl
root=Path.cwd(); src=(root/'src/main/java/dev/weaverlib/client/AngelMesh.java').read_text()
def depth(source,cls):
 text=source.split('class '+cls+' extends RenderType',1)[1].split('private '+cls+'()',1)[0]
 return re.search(r'setWriteMaskState\((\w+)\)',text).group(1)=='COLOR_DEPTH_WRITE'
old_mask=depth(src,'Layer');new_mask=depth(src,'Wings');glow_mask=depth(src,'Layer')
z=zipfile.ZipFile(root/'build/moddev/artifacts/neoforge-21.1.256-client-extra-aka-minecraft-resources.jar')
ctx=moderngl.create_standalone_context(require=320,backend='egl')
p=ctx.program(vertex_shader=z.read('assets/minecraft/shaders/core/position_color.vsh').decode(),
 fragment_shader=z.read('assets/minecraft/shaders/core/position_color.fsh').decode())
ident=array('f',[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]).tobytes()
p['ModelViewMat'].write(ident);p['ProjMat'].write(ident);p['ColorModulator'].value=(1,1,1,1)
fbo=ctx.simple_framebuffer((32,32),components=4);fbo.use();ctx.enable(moderngl.DEPTH_TEST|moderngl.BLEND)
ctx.depth_func='<=';ctx.blend_func=(moderngl.SRC_ALPHA,moderngl.ONE_MINUS_SRC_ALPHA,moderngl.ONE,moderngl.ONE_MINUS_SRC_ALPHA)
def quad(color,zpos,write):
 fbo.depth_mask=write
 data=[]
 for x,y in [(-1,-1),(1,-1),(1,1),(-1,-1),(1,1),(-1,1)]:data.extend([x,y,zpos,*color])
 buf=ctx.buffer(array('f',data).tobytes());vao=ctx.vertex_array(p,[(buf,'3f 4f','Position','Color')]);vao.render();vao.release();buf.release()
def reset():
 fbo.use();fbo.depth_mask=True;fbo.clear(.04,.06,.09,1,depth=1)
def pixel():return tuple(fbo.read(viewport=(16,16,1,1),components=4))
def same(a,b):return max(abs(x-y) for x,y in zip(a,b))<=2
wing=(1,.67,.2,.92)
checks=0
for name,color in [('entity',(1,0,0,1)),('water',(0,.25,1,.65)),('cloud',(.9,.95,1,.8))]:
 reset();quad(wing,0,old_mask);before=pixel();quad(color,.5,True);assert not same(pixel(),before),name+' must reproduce old bug'
 reset();quad(wing,0,new_mask);before=pixel();quad(color,.5,True);assert same(pixel(),before),name+' behind feathers leaked'
 reset();quad(wing,0,new_mask);before=pixel();quad(color,-.5,True);assert not same(pixel(),before),name+' in front wrongly hidden'
 checks+=3
reset();quad((1,.67,.2,.2),0,glow_mask);before=pixel();quad((0,.3,1,1),.5,True);assert not same(pixel(),before),'Fringe must not mask scenery';checks+=1
reset();quad((1,.67,.2,0),0,new_mask);quad((0,.3,1,1),.5,True);assert pixel()[2]>250,'Zero-alpha gap must not write depth';checks+=1
# Real vanilla Fabulous compositor: entities/clouds have independent depth targets and are sorted at composition.
composite=ctx.program(vertex_shader='''#version 150
in vec2 Position;out vec2 texCoord;void main(){gl_Position=vec4(Position,0,1);texCoord=Position*.5+.5;}''',
 fragment_shader=z.read('assets/minecraft/shaders/program/transparency.fsh').decode())
verts=ctx.buffer(array('f',[-1,-1,1,-1,1,1,-1,-1,1,1,-1,1]).tobytes())
vao=ctx.vertex_array(composite,[(verts,'2f','Position')]);ctx.disable(moderngl.DEPTH_TEST|moderngl.BLEND)
layers=['Diffuse','Translucent','ItemEntity','Particles','Weather','Clouds']
def fabulous(changed,main_depth,other_depth):
 textures=[]
 for i,name in enumerate(layers):
  rgba=(.85,.58,.18,1) if i==0 else ((.05,.15,.7,.8) if name==changed else (0,0,0,0))
  dep=main_depth if i==0 else other_depth
  for slot,(suffix,components,data) in enumerate([('Sampler',4,rgba),('DepthSampler',1,(dep,))]):
   tex=ctx.texture((1,1),components,array('f',data).tobytes(),dtype='f4');tex.use(i*2+slot);textures.append(tex)
   composite[name+suffix].value=i*2+slot
 fbo.use();vao.render();result=pixel()
 for tex in textures:tex.release()
 return result
reference=fabulous('none',.5,.75)
for layer in ['Translucent','ItemEntity','Clouds']:
 assert not same(fabulous(layer,1,.75),reference),layer+' old Fabulous leak not reproduced'
 assert same(fabulous(layer,.5,.75),reference),layer+' behind wing in Fabulous'
 assert not same(fabulous(layer,.5,.25),reference),layer+' in front wrongly hidden in Fabulous'
 checks+=3
assert new_mask and not glow_mask
print(f'Passed {checks} GPU depth/compositing checks using {ctx.info["GL_RENDERER"]}.')
