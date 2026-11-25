/* medslave · card.js: the card face inside the app's WebView. A copy of the site's js/card.js (face, cloze, shapes, IO one by one,
   trimF, MathJax, sequential clozes, flip/fade), driven by the app through R.* and reporting back through the App bridge:
   App.st(left, n): sequential clozes still hidden + the step size. App.tap(): a pulsing cloze was tapped (= Next).
   App.h(px): the card's height in CSS px (Reels Mode keeps tall cards above the account row). App.ready(seq): a draw is on the page */
const $=s=>document.querySelector(s),HR='<hr class="prettify-divider prettify-divider--answer">',DV='<div class="prettify-divider prettify-divider--answer"></div>';
let DATA={paths:[]},SET={source:false},cur=null,stage=0,FL=false;
const A_=window.App||{st(){},tap(){},h(){},ready(){}};
const deck=pi=>`<div class="prettify-deck">${(DATA.paths[pi]||'').split(' › ').map(x=>`<span class="prettify-subdeck">${x}</span>`).join('&nbsp;/&nbsp;')}</div>`;
const tags=()=>'';
const fld=(c,x,z='center-text')=>`<div class="prettify-field prettify-field--${c}"><div class="${z}">${x}</div></div>`;
const ttl=t=>t?`<div class="prettify-field prettify-field--front center-text"><h3 class="title">${t}</h3></div>`+HR:'';
/* MathJax: \( … \) and \[ … \], typeset as SVG by vendor/tex-svg.js, loaded the first time a card has math */
const MJX=/\\\(|\\\[/;let MJP=null;
function mjLoad(){if(MJP)return MJP;window.MathJax={tex:{inlineMath:[['\\(','\\)']],displayMath:[['\\[','\\]']]},svg:{fontCache:'local'},startup:{typeset:false},options:{enableMenu:false}};
 MJP=new Promise((ok,no)=>{const s=document.createElement('script');s.src='/vendor/tex-svg.js';s.async=true;s.onload=()=>MathJax.startup.promise.then(ok,no);s.onerror=()=>{MJP=null;no()};document.head.appendChild(s)});return MJP}
function mj(el){if(!el||!MJX.test(el.textContent))return;mjLoad().then(()=>MathJax.typesetPromise([el])).then(()=>fitX()).catch(()=>{})}
const mathClz=(t,k,ans)=>t.replace(/\\\([\s\S]*?\\\)|\\\[[\s\S]*?\\\]/g,m=>m.replace(/\{\{c(\d+)::([\s\S]*?)(?:::([\s\S]*?))?\}\}/g,(z,c,x,h)=>+c==k&&!ans?'\\text{['+(h||'...')+']}':x));
const cloze=(t,k,ans,ha)=>mathClz(t,k,ans).replace(/\{\{c(\d+)::([\s\S]*?)(?:::([\s\S]*?))?\}\}/g,(m,c,x,h)=>+c==k?`<span class="cloze" data-cloze="${x.replace(/"/g,'&quot;')}" data-ordinal="${c}">${ans?x:'['+(h||'...')+']'}</span>`:ha?`<span class="cloze-inactive cloze" data-cloze="${x.replace(/"/g,'&quot;')}" data-ordinal="${c}">[...]</span>`:`<span class="cloze-inactive" data-ordinal="${c}">${x}</span>`);
function shapes(t,k,A){let o='';const re=/\{\{c(\d+)::image-occlusion:(\w+):([^}]*)\}\}/g;let m;
while(m=re.exec(t)){if(+m[1]!=k)continue;const p={};m[3].split(':').forEach(x=>{const i=x.indexOf('=');p[x.slice(0,i)]=x.slice(i+1).replace(/"/g,'')});
const st=A?'fill="rgba(255,235,162,.25)" stroke="#ffeba2" stroke-width=".005"':'fill="#ffeba2" stroke="#212121" stroke-width=".004"';
if(m[2]=='rect')o+=`<rect x="${p.left}" y="${p.top}" width="${p.width}" height="${p.height}" ${st}/>`;
else if(m[2]=='ellipse')o+=`<ellipse cx="${+p.left+ +p.rx}" cy="${+p.top+ +p.ry}" rx="${p.rx}" ry="${p.ry}" ${st}/>`;
else if(m[2]=='polygon')o+=`<polygon points="${p.points}" ${st}/>`}return o}
/* IO one by one (Closet rects [[rect0::left,top,width,height]] in image pixels): the front uncovers the boxes in order as sequential steps */
const IOR=/\[\[rect\d*::\s*([\d.]+),\s*([\d.]+),\s*([\d.]+),\s*([\d.]+)/g;
const rects=(t,A)=>[...t.matchAll(IOR)].map(([,x,y,w,h])=>`<rect class="${A?'io-a':'cloze io-r'}" x="${x}" y="${y}" width="${w}" height="${h}"/>`).join('');
function ioBox(i){const s=i.nextElementSibling;if(i.naturalWidth&&s&&s.classList.contains('io-px'))s.setAttribute('viewBox',`0 0 ${i.naturalWidth} ${i.naturalHeight}`)}
/* stray blank lines at a field's ends, at a list's or item's ends and after headings */
const BL='(?:\\s|&nbsp;|<br\\b[^>]*>|<div>(?:\\s|&nbsp;|<br\\b[^>]*>)*</div>)+',BLS=new RegExp('^'+BL,'i'),BLE=new RegExp(BL+'$','i');
const LBA=/(<li\b[^>]*>(?:\s*<(?:div|p|span|b|strong|i|em|u|font)\b[^>]*>)*|<\/?(?:ul|ol)\b[^>]*>|<\/(?:li|h[1-6])>)(?:\s|<br\b[^>]*>)+/gi,
 LBB=/(?:<br\b[^>]*>\s*)+(?=(?:<\/(?:div|p|span|b|strong|i|em|u|font)>\s*)*(?:<\/(?:li|ul|ol)>|<(?:li|ul|ol)\b))/gi;
const trimF=x=>typeof x=='string'?x.replace(LBA,'$1').replace(LBB,'').replace(BLS,'').replace(BLE,''):x;
function face(A){const{n,o}=cur,[,k,pi]=n,f=n[4].map(trimF);let h;
if(k=='b'){const[fr,bk,ex,ti,so,ht]=f,sr=SET.source?so:'',q=o?bk:fr;
h=deck(pi)+HR+(A?ttl(ti)+(f[6]=='ioe'?'':fld('front',q)+HR)+fld('back',o?fr:bk)+(ex?HR+fld('back',ex,'center-text extra'):'')+(!o&&sr?DV+fld('front zoomable-image',sr,'center-text extra'):'')+tags()
:(o?'':ttl(ti))+fld('front',q)+(o?'':tags()))}
else if(k=='c'){const[tx,ex,ti,so,ht,lf,sq,ha]=f,sr=SET.source?so:'',L=!lf,sn=(sq||'').trim()?(Number(sq)||1):0,sb=sn&&!A?`<div class="sequentialCloze" data-n="${sn}">`:'',se=sn&&!A?'</div>':'',ab=A&&ha&&[...tx.matchAll(/\{\{c(\d+)::/g)].some(m=>+m[1]!=o+1)?'<div class="center-text"><button id="allClozeBtn">Reveal all clozes</button></div>':'',w=x=>`<div class="prettify-field prettify-field--front zoomable-image cloze-extra">${L?'<div class="center-text extra">':''}${x}${L?'</div>':''}</div>`;
h=deck(pi)+DV+`<div class="prettify-field prettify-field--front">${sb}${L?'<div class="center-text">':''}${ti?`<h3 class="title">${ti}</h3><div class="shift-right">`:''}${cloze(tx,o+1,A,ha)}${ab}${ti?'</div>':''}${L?'</div>':''}${se}</div>`
+(A&&ex?DV+w(ex):'')+(A&&sr?DV+w(sr):'')+tags()}
else{const[oc,hd,im,ft,...r]=f,x=r.filter(Boolean),g=y=>HR+fld('front extra',`<div id="io-footer">${y}</div>`),px=oc.search(IOR)>=0,io=`<div class="io-c" id="image-occlusion-container">${im}<svg ${px?'class="io-px" viewBox="0 0 0 0"':'viewBox="0 0 1 1"'} preserveAspectRatio="none">${px?rects(oc,A):shapes(oc,o+1,A)}</svg></div>`;
h=deck(pi)+HR+(hd?fld('front',`<div id="io-header">${hd}</div>`)+HR:'')+fld('front',px&&!A?`<div class="sequentialCloze" data-n="1">${io}</div>`:io)+(ft?g(ft):'')+(A?x.slice(0,5).map(g).join(''):'')+tags()}
return `<div class="prettify-flashcard${k=='c'?' prettify-flashcard-front':''}">${h}</div>`}
function render(){if(!FL){const A=stage==1;return `<div class="tinder-swipe${A?' tinder-swipe--answer':''}" id="swipe">${face(A)}</div>`}
const m=cur.n[1]=='b'?'flip':'fade';   /* basic cards flip; cloze + image-occlusion fade in place */
return `<div class="tinder-swipe" id="swipe"><div class="${m}-wrap${stage?' flipped half':''}"><div class="${m}-front">${face(0)}</div><div class="${m}-back">${face(1)}</div></div></div>`}
const medURL=k=>'/media/'+encodeURIComponent(k);
function draw(){const st=$('#stage');st.innerHTML='<div class="wrap">'+render()+'</div>';scrollTo(0,0);
 st.querySelectorAll('img[data-m]').forEach(i=>{i.src=medURL(i.dataset.m)});
 mj(st);st.querySelectorAll('.prettify-deck').forEach(d=>d.scrollLeft=d.scrollWidth);fitX();RSZ.disconnect();RSZ.observe(st.firstElementChild);
 {const b=$('#allClozeBtn');if(b)b.onclick=()=>revealAll(b)}syncNxt()}
/* tables and the deck path that fit get .nsx; the card's height goes to the app */
function fitX(){document.querySelectorAll('#stage table,#stage .prettify-deck,#stage [style*="overflow"]').forEach(e=>e.classList.toggle('nsx',e.scrollWidth<=e.clientWidth+1));document.querySelectorAll('#stage .prettify-deck').forEach(deckFade);
 const w=$('#stage').firstElementChild;if(w)A_.h(w.offsetHeight)}
const RSZ=new ResizeObserver(()=>fitX());
function deckFade(d){d.classList.toggle('sl',d.scrollLeft>1);d.classList.toggle('sr',d.scrollLeft+d.clientWidth<d.scrollWidth-1)}
$('#stage').addEventListener('scroll',e=>{const d=e.target;if(d.classList&&d.classList.contains('prettify-deck'))deckFade(d)},true);
addEventListener('resize',fitX);$('#stage').addEventListener('load',e=>{if(e.target.tagName=='IMG')ioBox(e.target);fitX()},true);
$('#stage').addEventListener('dragstart',e=>e.preventDefault());
const front=()=>document.querySelector('#stage .flip-front,#stage .fade-front')||$('#stage');
function reveal(n){const q='.cloze:not(.cloze-inactive):not(.cloze-revealed)',r=front();[...r.querySelectorAll(q)].slice(0,n).forEach(c=>{if(c.hasAttribute('data-cloze'))c.innerHTML=c.getAttribute('data-cloze');c.classList.add('cloze-revealed')});mj(r);syncNxt()}
function seqLeft(){if(stage||!document.querySelector('#stage .sequentialCloze'))return 0;return front().querySelectorAll('.cloze:not(.cloze-inactive):not(.cloze-revealed)').length}
function seqN(){const s=document.querySelector('#stage .sequentialCloze');return Math.max(1,+(s&&s.dataset.n)||1)}
/* the app's Next button shows 'Next Cloze' while more is hidden than one step reveals; the next group pulses */
function syncNxt(){const n=seqLeft();document.querySelectorAll('#stage .cloze-pulse').forEach(e=>e.classList.remove('cloze-pulse'));
 if(n)[...front().querySelectorAll('.cloze:not(.cloze-inactive):not(.cloze-revealed)')].slice(0,seqN()).forEach(f=>f.classList.add('cloze-pulse'));A_.st(n,seqN())}
$('#stage').addEventListener('click',e=>{if(e.target.closest&&e.target.closest('.cloze-pulse'))A_.tap()});
function revealAll(b){const r=document.querySelector('#stage .flip-back,#stage .fade-back')||$('#stage');r.querySelectorAll('.cloze-inactive[data-cloze]').forEach(c=>{c.innerHTML=c.getAttribute('data-cloze');c.classList.add('cloze-revealed')});mj(r);b.style.display='none'}
/* the answer in Flip: basic cards flip, cloze + occlusion fade; the fade glides the card's height from the question's to the answer's */
function answer(rm){stage=1;const w=document.querySelector('#stage .flip-wrap,#stage .fade-wrap');if(!w)return;scrollTo(0,0);
 if(w.classList.contains('fade-wrap')&&!rm){const h0=w.querySelector('.fade-front').offsetHeight,b=w.querySelector('.fade-back>.prettify-flashcard'),h1=b?b.offsetHeight:h0;w.classList.add('flipped');
  const an=w.animate([{height:h0+'px'},{height:h1+'px'}],{duration:350,easing:'ease',fill:'forwards'});an.onfinish=()=>{w.classList.add('half');an.cancel();fitX()}}
 else{w.classList.add('flipped');setTimeout(()=>{w.classList.add('half');fitX()},rm?0:76)}syncNxt()}
/* underglow on the answer side: v from -1 (missed it, red) to 1 (knew it, green) */
function glow(v){const k=$('#swipe');if(!k)return;k.classList.toggle('gl-ok',v>0);k.classList.toggle('gl-no',v<0);k.style.setProperty('--glow-opacity',(Math.min(1,Math.abs(v))*.55).toFixed(3))}
/* the app's calls */
const R={
 init(paths,source){DATA.paths=paths;SET.source=!!source},
 source(v){SET.source=!!v},
 theme(t,dark){document.documentElement.dataset.theme=t;document.body.classList.toggle('night_mode',dark);document.body.classList.toggle('nightMode',dark)},
 show(seq,n,o,s,flip){cur={n,o};stage=s;FL=!!flip;draw();requestAnimationFrame(()=>A_.ready(seq))},
 clear(){cur=null;$('#stage').innerHTML='';A_.h(0)},
 answer(rm){answer(rm)},
 step(){reveal(seqN())},
 glow(v){glow(v)},
 long(v){$('#stage').classList.toggle('long',!!v)}};
