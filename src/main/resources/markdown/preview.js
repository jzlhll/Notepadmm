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
        var target=document.getElementById(decodeURIComponent(destination.substring(1)));
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
