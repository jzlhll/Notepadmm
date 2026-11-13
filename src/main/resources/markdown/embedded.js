/* 编辑区内的固定块预览脚本；图片由共用后台加载器读取，页面不直接发起外部请求。 */
var embeddedObserver;
var embeddedHeightPending=false, embeddedLastHeight=-1;
function embeddedTask(line,checked) {
  var item=document.querySelector('li[data-source-line="'+line+'"]');
  var input=item && item.querySelector('input[type=checkbox]');
  if(input) {input.checked=checked;input.defaultChecked=checked;}
}
function embeddedTasks(states) {
  states.forEach(function(state) {embeddedTask(state[0],state[1]);});
}
function embeddedHeight() {
  if(embeddedHeightPending || typeof embeddedBridge==='undefined') return;
  embeddedHeightPending=true;
  requestAnimationFrame(function() {
    embeddedHeightPending=false;
    var article=document.querySelector('article');
    if(!article) return;
    var height=Math.ceil(article.getBoundingClientRect().height);
    if(height===embeddedLastHeight) return;
    embeddedLastHeight=height;
    embeddedBridge.height(height);
  });
}
function embeddedReadonly(readonly) {
  document.body.setAttribute('data-readonly',readonly);
  document.querySelectorAll('input[type=checkbox]').forEach(function(input) {input.disabled=readonly;});
  document.querySelectorAll('.md-image-edit').forEach(function(button) {button.disabled=readonly;});
}
function embeddedImageSize(image) {
  if(!image.embeddedFrame) return;
  var width=image.embeddedWidth || image.embeddedSourceWidth || 320;
  var percent=image.embeddedPercent;
  var display=percent===null || typeof percent==='undefined' ? (image.embeddedSourceWidth || width) : width*percent/100;
  var buttons=image.embeddedFrame.querySelectorAll('.md-image-tools>button');
  var toolsWidth=0;
  buttons.forEach(function(button){toolsWidth+=button.getBoundingClientRect().width+2;});
  image.embeddedFrame.style.width=Math.max(display,Math.ceil(toolsWidth))+'px';
  image.style.width=display+'px';
}
function embeddedImageControls(config) {
  config.forEach(function(item,index) {
    var image=document.querySelector('img[data-image-id="'+index+'"]');
    if(!image) return;
    if(!image.embeddedFrame) {
      image.embeddedSourceWidth=Number(image.getAttribute('width')) || 0;
      var frame=document.createElement('span');frame.className='md-image-container';
      image.parentNode.insertBefore(frame,image);frame.appendChild(image);image.embeddedFrame=frame;
      var tools=document.createElement('span');tools.className='md-image-tools';frame.insertBefore(tools,image);
      var labels=window.embeddedImageLabels;
      function button(label,action) {
        var control=document.createElement('button');control.type='button';control.textContent=label;
        control.onclick=function(event) {event.preventDefault();event.stopPropagation();action(control);};
        tools.appendChild(control);return control;
      }
      button(labels[0],function(){embeddedBridge.imageJump(index,false);});
      button(labels[1],function(){embeddedBridge.imageJump(index,true);});
      image.embeddedZoomButton=button(labels[2],function(control){
        var bounds=control.getBoundingClientRect();embeddedBridge.imageZoom(index,bounds.left,bounds.bottom);
      });
      if(item.local) {
        var edit=button(labels[3],function(){embeddedBridge.imageEdit(index);});
        edit.className='md-image-edit';edit.disabled=document.body.getAttribute('data-readonly')==='true';
      }
    }
    image.embeddedPercent=item.percent;
    image.embeddedZoomButton.textContent=window.embeddedImageLabels[2]+(item.percent==null?100:item.percent)+'%';
    embeddedImageSize(image);
  });
  embeddedHeight();
}
function embeddedImage(index,data,width,height) {
  var image=document.querySelector('img[data-image-id="'+index+'"]');
  if(!image) return;
  image.removeAttribute('data-image-loading');
  if(image.nextElementSibling && image.nextElementSibling.classList.contains('image-retry')) image.nextElementSibling.remove();
  if(data) {
    image.embeddedWidth=width;
    embeddedImageSize(image);
    image.src=data;
  } else {
    var button=document.createElement('button');button.type='button';button.className='image-retry';
    button.textContent=image.alt || 'Image';
    button.onclick=function(){button.disabled=true;embeddedBridge.retry(index);};
    image.after(button);
  }
  embeddedHeight();
}
function embeddedReady() {
  window.editorBridge=embeddedBridge;
  document.addEventListener('dblclick',function(event) {
    if(event.target.closest('a,input,button,summary,.md-task-control')) return;
    var hit=document.caretRangeFromPoint?document.caretRangeFromPoint(event.clientX,event.clientY):null;
    var text=hit && hit.startContainer.parentElement?hit.startContainer.parentElement.closest('[data-source-text]'):null;
    if(text) {
      var range=document.createRange();range.selectNodeContents(text);range.setEnd(hit.startContainer,hit.startOffset);
      var offset=range.toString().length, mapping=text.getAttribute('data-source-map');
      if(mapping){var positions=mapping.split(',');offset=Number(positions[Math.min(offset,positions.length-1)]);}
      embeddedBridge.edit(Number(text.getAttribute('data-source-start'))+offset);
      return;
    }
    var node=event.target.closest('[data-source-start]');
    embeddedBridge.edit(node?Number(node.getAttribute('data-source-start')):-1);
  });
  document.addEventListener('click',function(event) {
    var anchor=event.target.closest('a');
    if(anchor) {
      event.preventDefault();
      if(event.ctrlKey || event.metaKey || anchor.closest('.md-toc,.footnotes,sup'))
        embeddedBridge.open(anchor.getAttribute('href') || '');
      else {
        var source=anchor.closest('[data-source-start]');
        embeddedBridge.edit(source?Number(source.getAttribute('data-source-start')):-1);
      }
    }
  });
  document.addEventListener('change',function(event) {
    if(!event.target.matches('input[type=checkbox]')) return;
    var item=event.target.closest('li[data-source-line]');
    if(item) embeddedBridge.task(Number(item.getAttribute('data-source-line')),event.target.checked);
  });
  embeddedObserver=new MutationObserver(embeddedHeight);
  embeddedObserver.observe(document.querySelector('article'),{subtree:true,childList:true,attributes:true,characterData:true});
  window.addEventListener('resize',embeddedHeight);
  document.addEventListener('load',embeddedHeight,true);
  document.addEventListener('toggle',embeddedHeight,true);
  if(document.fonts) document.fonts.ready.then(embeddedHeight);
  embeddedHeight();
}
