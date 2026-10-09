/* 只由独立输出页面调用；等待排版完成后，在行、表格行及图片边界分页。 */
document.querySelectorAll('details').forEach(function(node) { node.open=true; });
async function preparePdf(width,height) {
  try {
    await diagramQueue;
    if(document.fonts && document.fonts.ready) await document.fonts.ready;
    document.querySelectorAll('details').forEach(function(node) { node.open=true; });
    await Promise.all(Array.from(document.images).map(function(image) {
      return new Promise(function(resolve,reject) {
        var timer;
        function finish() {
          clearTimeout(timer);
          image.removeEventListener('load',finish); image.removeEventListener('error',finish);
          if(image.naturalWidth>0) resolve(); else reject(new Error('Image load failed: '+image.getAttribute('src')));
        }
        if(image.complete) { finish(); return; }
        image.addEventListener('load',finish); image.addEventListener('error',finish);
        timer=setTimeout(function() {
          image.removeEventListener('load',finish); image.removeEventListener('error',finish);
          reject(new Error('Image load timed out: '+image.getAttribute('src')));
        },20000);
      });
    }));
    document.documentElement.style.setProperty('--pdf-page-height',height+'px');
    // 表格只按宽度缩放并跨页，公式及图表还须适应页高；同步容器高度。
    document.querySelectorAll('table,.md-math[data-display=true],.mermaid-diagram').forEach(function(node) {
      if(node.hidden || node.closest('[hidden]')) return;
      var box=node.getBoundingClientRect(), naturalWidth=Math.max(box.width,node.scrollWidth);
      var factor=Math.min(1,width/naturalWidth);
      if(node.tagName!=='TABLE') factor=Math.min(factor,height/box.height);
      if(factor>=1 || !isFinite(factor) || factor<=0) return;
      var wrapper=document.createElement('div'); wrapper.className='pdf-fit';
      node.before(wrapper); wrapper.appendChild(node);
      wrapper.style.height=(box.height*factor)+'px';
      node.style.width=naturalWidth+'px'; node.style.margin='0'; node.style.transform='scale('+factor+')';
    });
    await new Promise(function(resolve) { requestAnimationFrame(function() { requestAnimationFrame(resolve); }); });
    var article=document.querySelector('article'), rectangles=[];
    function rectangle(node) {
      var box=node.getBoundingClientRect();
      return {top:box.top+window.scrollY,bottom:box.bottom+window.scrollY};
    }
    var walker=document.createTreeWalker(article,NodeFilter.SHOW_TEXT), node;
    while((node=walker.nextNode())) {
      if(!node.textContent.trim() || node.parentElement.closest('[hidden],button,.code-tools,.md-task-source,.katex-mathml')) continue;
      var range=document.createRange(); range.selectNodeContents(node);
      Array.from(range.getClientRects()).forEach(function(box) {
        if(box.height>0 && box.height<height) rectangles.push({top:box.top+window.scrollY,bottom:box.bottom+window.scrollY});
      });
    }
    article.querySelectorAll('tr,img,svg,.md-math[data-display=true],.pdf-fit,h1,h2,h3,h4,h5,h6').forEach(function(item) {
      if(item.closest('[hidden]')) return;
      var box=rectangle(item);
      if(/^H[1-6]$/.test(item.tagName)) {
        var next=item.nextElementSibling || item.parentElement.nextElementSibling;
        if(next) box.bottom=Math.max(box.bottom,rectangle(next).top+24);
      }
      if(box.bottom>box.top && box.bottom-box.top<=height) rectangles.push(box);
    });
    rectangles.sort(function(a,b) {return a.top-b.top;});
    var bottom=Math.ceil(article.getBoundingClientRect().bottom+window.scrollY), cuts=[0];
    while(cuts[cuts.length-1]<bottom) {
      var start=cuts[cuts.length-1], end=Math.min(bottom,start+height), previous;
      // 某行与父表格行可能同时跨界，逐次向前收敛，不能切开其中任一项。
      do {
        previous=end;
        for(var i=0;i<rectangles.length && rectangles[i].top<end;i++) {
          var box=rectangles[i];
          if(box.bottom>end && box.top>start+1) end=Math.max(start+1,Math.floor(box.top));
        }
      } while(end<previous);
      cuts.push(end);
      if(cuts.length>1001) throw new Error('PDF page limit exceeded');
    }
    if(cuts.length===1) cuts.push(1);
    // 最后一页也能滚到其起点，不受 WebView 的最大滚动位置限制。
    var spacer=document.createElement('div'); spacer.style.height=(height+2)+'px'; document.body.appendChild(spacer);
    pdfBridge.ready(JSON.stringify(cuts));
  } catch(error) { pdfBridge.failed(String(error)); }
}
function showPdfPage(top) { window.scrollTo(0,top); }
