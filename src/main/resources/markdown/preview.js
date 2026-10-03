/* 固定预览脚本只处理定位、任务勾选和链接；文档源码以转义文本传入。 */
function installPreviewBridge() {
  document.addEventListener('dblclick', function(event) {
    var node=event.target.closest('[data-source-start]');
    if(node && !event.target.closest('a,input,button')) editorBridge.edit(Number(node.getAttribute('data-source-start')));
  });
  document.addEventListener('click', function(event) {
    var anchor=event.target.closest('a');
    if(anchor) {
      event.preventDefault();
      var destination=anchor.getAttribute('href');
      if(destination && destination.charAt(0)==='#') {
        var name=destination.substring(1);
        try { name=decodeURIComponent(name); } catch(error) { }
        var target=document.getElementById(name);
        if(target) target.scrollIntoView();
      } else if(destination) editorBridge.open(destination);
    }
  });
  document.querySelectorAll('input[type=checkbox]').forEach(function(input) {
    var item=input.closest('li[data-source-line]');
    if(!item) return;
    input.disabled=document.body.getAttribute('data-readonly')==='true';
    input.addEventListener('change',function(){ editorBridge.task(Number(item.getAttribute('data-source-line')),input.checked); });
  });
}
function followSource(position) {
  var old=document.querySelector('.current-block'); if(old) old.classList.remove('current-block');
  var nodes=document.querySelectorAll('article [data-source-start]'), chosen=null;
  for(var i=0;i<nodes.length;i++) if(Number(nodes[i].getAttribute('data-source-start'))<=position) chosen=nodes[i];
  if(chosen) { chosen.classList.add('current-block'); chosen.scrollIntoView({block:'nearest'}); }
}

/* 公式失败时保留原文；图表按顺序渲染，避免同时占用多个 WebKit 任务。 */
function renderTechnicalContent() {
  document.querySelectorAll('.md-math').forEach(function(node) {
    var source=node.textContent;
    if(typeof katex==='undefined') return;
    try { katex.render(source,node,{displayMode:node.getAttribute('data-display')==='true',throwOnError:true,trust:false,maxSize:20,maxExpand:1000}); }
    catch(error) { node.textContent=source; node.classList.add('math-error'); node.title=error.message; }
  });
  document.querySelectorAll('pre > code').forEach(function(code) {
    var bar=document.createElement('div');bar.className='code-tools';
    var language=document.createElement('span');language.textContent=(code.className || '').replace('language-','');
    var copy=document.createElement('button');copy.type='button';copy.textContent=document.body.getAttribute('data-copy-label') || 'Copy';
    copy.onclick=function() {
      if(typeof editorBridge!=='undefined') editorBridge.copy(code.textContent);
      else {
        var field=document.createElement('textarea');field.value=code.textContent;document.body.appendChild(field);
        field.select();document.execCommand('copy');field.remove();
      }
    };
    bar.appendChild(language);bar.appendChild(copy);code.parentNode.before(bar);
  });
  document.querySelectorAll('img').forEach(function(image) {
    image.addEventListener('error',function() {
      if(image.nextElementSibling && image.nextElementSibling.classList.contains('image-retry')) return;
      var retry=document.createElement('button'); retry.type='button'; retry.className='image-retry'; retry.textContent=image.alt || 'Image';
      retry.title=image.getAttribute('src');
      retry.onclick=function(){ retry.remove(); image.src=image.getAttribute('src'); };
      image.after(retry);
    });
  });
  if(typeof mermaid!=='undefined') {
    mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:document.body.classList.contains('dark')?'dark':'default',maxTextSize:100000});
    var blocks=Array.from(document.querySelectorAll('.mermaid-source'));
    (async function() {
      for(var i=0;i<blocks.length;i++) {
        var source=blocks[i], diagram=document.createElement('div'); diagram.className='mermaid-diagram';
        diagram.setAttribute('data-source-start',source.getAttribute('data-source-start'));
        try {
          await mermaid.parse(source.textContent);
          var result=await mermaid.render('diagram-'+i,source.textContent);
          diagram.innerHTML=result.svg;
          var button=document.createElement('button');button.type='button';button.textContent='</>';
          button.onclick=(function(code,picture){return function(){var showing=code.hidden;code.hidden=!showing;picture.hidden=showing;};})(source,diagram);
          source.before(button);source.after(diagram);source.hidden=true;
        } catch(error) {
          source.title=error.message;source.classList.add('math-error');
          var message=document.createElement('div');message.className='diagram-error';message.textContent=error.message;source.after(message);
        }
      }
    })();
  }
}
if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',renderTechnicalContent);
else renderTechnicalContent();
