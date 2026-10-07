/* 固定预览脚本只处理定位、任务勾选和链接；文档源码以转义文本传入。 */
function installPreviewBridge() {
  if(document.previewBridgeInstalled) return;
  document.previewBridgeInstalled=true;
  document.addEventListener('dblclick', function(event) {
    var node=event.target.closest('[data-source-start]');
    if(node && !event.target.closest('a,input,button,.md-task-control')) editorBridge.edit(Number(node.getAttribute('data-source-start')));
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
  document.addEventListener('change',function(event) {
    var input=event.target;
    if(!input.matches('input[type=checkbox]')) return;
    var item=input.closest('li[data-source-line]');
    if(item) editorBridge.task(Number(item.getAttribute('data-source-line')),input.checked);
  });
}

/* 只复用内容相同的顶层块；坐标单独更新，公式、图表及折叠状态留在原节点中。 */
function previewBlocks(container) {
  return Array.from(container.childNodes).filter(function(node) {
    return node.nodeType===1 || node.textContent.trim();
  }).map(function(node) {
    var block=container.ownerDocument.createElement('div'); block.className='preview-block';
    container.replaceChild(block,node); block.appendChild(node);
    block.sourceNodes=Array.from(block.querySelectorAll('[data-source-start],[data-source-line]'));
    var clean=block.cloneNode(true);
    clean.querySelectorAll('[data-source-start],[data-source-line]').forEach(function(item) {
      ['data-source-start','data-source-line'].forEach(function(name) {
        if(item.hasAttribute(name)) item.setAttribute(name,'');
      });
    });
    block.sourceKey=clean.innerHTML;
    return block;
  });
}
function previewReadingState() {
  return JSON.stringify({x:window.scrollX,y:window.scrollY,details:Array.from(document.querySelectorAll('details')).map(function(node) {
    var summary=node.querySelector('summary'); return {title:summary?summary.textContent:'',open:node.open};
  })});
}
function restorePreviewReadingState(state) {
  var saved=JSON.parse(state), titles=new Map();
  saved.details.forEach(function(item) {
    if(!titles.has(item.title)) titles.set(item.title,[]);
    titles.get(item.title).push(item.open);
  });
  document.querySelectorAll('details').forEach(function(node) {
    var summary=node.querySelector('summary'), values=titles.get(summary?summary.textContent:'');
    if(values && values.length) node.open=values.shift();
  });
  window.scrollTo(saved.x,saved.y);
}
function updatePreview(content) {
  var article=document.querySelector('article'), saved=previewReadingState(), known=new Map();
  var anchor=Array.from(article.children).find(function(block) {return block.getBoundingClientRect().bottom>0;});
  var offset=anchor?anchor.getBoundingClientRect().top:0;
  Array.from(article.children).forEach(function(block) {
    if(!known.has(block.sourceKey)) known.set(block.sourceKey,[]);
    known.get(block.sourceKey).push(block);
  });
  // 模板中的图片不启动请求，只有真正新增的块插入页面后才加载。
  var incoming=document.createElement('template'); incoming.innerHTML=content.body;
  var blocks=previewBlocks(incoming.content), cursor=article.firstChild;
  blocks.forEach(function(fresh) {
    var matches=known.get(fresh.sourceKey), block=matches && matches.length?matches.shift():fresh;
    if(block!==fresh) block.sourceNodes.forEach(function(node,index) {
      ['data-source-start','data-source-line'].forEach(function(name) {
        var value=fresh.sourceNodes[index].getAttribute(name);
        if(value===null) node.removeAttribute(name); else node.setAttribute(name,value);
      });
    });
    if(block!==cursor) article.insertBefore(block,cursor); else cursor=cursor.nextSibling;
  });
  while(cursor) {var next=cursor.nextSibling; cursor.remove(); cursor=next;}
  document.querySelectorAll('.mermaid-source').forEach(function(source) {
    if(source.diagram) source.diagram.setAttribute('data-source-start',source.getAttribute('data-source-start'));
  });
  document.body.style.fontSize=content.fontSize+'px';
  document.body.style.fontFamily=content.fontFamily;
  document.body.setAttribute('data-readonly',content.readonly);
  renderTechnicalContent();
  restorePreviewReadingState(saved);
  if(anchor && anchor.isConnected) window.scrollBy(0,anchor.getBoundingClientRect().top-offset);
}
function followSource(position) {
  var old=document.querySelector('.current-block'); if(old) old.classList.remove('current-block');
  var nodes=document.querySelectorAll('article [data-source-start]'), chosen=null;
  for(var i=0;i<nodes.length;i++) if(Number(nodes[i].getAttribute('data-source-start'))<=position) chosen=nodes[i];
  if(chosen) { chosen.classList.add('current-block'); chosen.scrollIntoView({block:'nearest'}); }
}

/* 公式失败时保留原文；图表按顺序渲染，避免同时占用多个 WebKit 任务。 */
var diagramQueue=Promise.resolve(), diagramId=0, mermaidInitialized=false;
function renderTechnicalContent() {
  document.querySelectorAll('input[type=checkbox]').forEach(function(input) {
    if(input.closest('li[data-source-line]')) input.disabled=document.body.getAttribute('data-readonly')==='true';
  });
  document.querySelectorAll('.md-math').forEach(function(node) {
    if(node.previewRendered) return;
    var source=node.textContent;
    if(typeof katex==='undefined') return;
    node.previewRendered=true;
    try { katex.render(source,node,{displayMode:node.getAttribute('data-display')==='true',throwOnError:true,trust:false,maxSize:20,maxExpand:1000}); }
    catch(error) { node.textContent=source; node.classList.add('math-error'); node.title=error.message; }
  });
  document.querySelectorAll('pre > code').forEach(function(code) {
    if(code.previewRendered) return;
    code.previewRendered=true;
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
    if(image.previewRendered) return;
    image.previewRendered=true;
    image.addEventListener('error',function() {
      if(image.nextElementSibling && image.nextElementSibling.classList.contains('image-retry')) return;
      var retry=document.createElement('button'); retry.type='button'; retry.className='image-retry'; retry.textContent=image.alt || 'Image';
      retry.title=image.getAttribute('src');
      retry.onclick=function(){ retry.remove(); image.src=image.getAttribute('src'); };
      image.after(retry);
    });
  });
  if(typeof mermaid!=='undefined') {
    if(!mermaidInitialized) {
      mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:document.body.classList.contains('dark')?'dark':'default',maxTextSize:100000});
      mermaidInitialized=true;
    }
    var blocks=Array.from(document.querySelectorAll('.mermaid-source')).filter(function(source) {
      if(source.previewRendered) return false;
      source.previewRendered=true; return true;
    });
    diagramQueue=diagramQueue.then(async function() {
      for(var i=0;i<blocks.length;i++) {
        var source=blocks[i], diagram=document.createElement('div'); diagram.className='mermaid-diagram';
        if(!source.isConnected) continue;
        diagram.setAttribute('data-source-start',source.getAttribute('data-source-start'));
        try {
          await mermaid.parse(source.textContent);
          if(!source.isConnected) continue;
          var result=await mermaid.render('diagram-'+(diagramId++),source.textContent);
          if(!source.isConnected) continue;
          diagram.innerHTML=result.svg;
          source.diagram=diagram;
          diagram.setAttribute('data-source-start',source.getAttribute('data-source-start'));
          var button=document.createElement('button');button.type='button';button.textContent='</>';
          button.onclick=(function(code,picture){return function(){var showing=code.hidden;code.hidden=!showing;picture.hidden=showing;};})(source,diagram);
          source.before(button);source.after(diagram);source.hidden=true;
        } catch(error) {
          if(!source.isConnected) continue;
          source.title=error.message;source.classList.add('math-error');
          var message=document.createElement('div');message.className='diagram-error';message.textContent=error.message;source.after(message);
        }
      }
    });
  }
}
function initializePreview() {previewBlocks(document.querySelector('article')); renderTechnicalContent();}
if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',initializePreview);
else initializePreview();
