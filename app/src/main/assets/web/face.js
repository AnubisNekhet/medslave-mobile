/* medslave · face.js: a card's HTML (basic, cloze, image occlusion, IO one by one, MathJax, trimmed blank lines), shared by
   card.html (the reviewer) and feed.html (Reels Mode's home feed). face(A) draws cur = {n, o}; A = the answer side */
const $=s=>document.querySelector(s),HR='<hr class="prettify-divider prettify-divider--answer">',DV='<div class="prettify-divider prettify-divider--answer"></div>';
let DATA={paths:[]},SET={source:false,expl:true,tags:false,hidex:false},cur=null,stage=0,FL=false;
const deck=pi=>`<div class="prettify-deck">${(DATA.paths[pi]||'').split(' › ').map(x=>`<span class="prettify-subdeck">${x}</span>`).join('&nbsp;/&nbsp;')}</div>`;
/* a card's tags at its bottom (Settings › Reviewing › Show tags): each tag's last part, as on the site's cards */
const tags=n=>SET.tags&&n[5]&&n[5].length?`<div class="prettify-tags">${n[5].map(t=>{const l=String(t).split('::').filter(Boolean).pop().replace(/_/g,' ').trim();return `<span class="prettify-tag${/neet|aiims|inicet/i.test(String(t))?' exam':''}">${l.replace(/</g,'&lt;')}</span>`}).join('')}</div>`:'';
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
/* MCQs (imported MCQ note types, e.g. AkiraRemix MCQs; stored as a basic card whose front lists the options and whose back marks the
   right one): drawn with AkiraRemix MCQs' own markup. the question side's options are shuffled (the answer side keeps that order);
   tapping one picks it (card.js). the answer side marks the right option (correct-answer if it was picked, highlight-correct if not)
   and a wrong pick (highlight-wrong); without a pick (posts, stories) it just marks the right one */
const MQ={key:'',order:[],picked:null};
function mcqOf(f){if(f[6]!='ioe'||!/<ol class="mcq"/.test(f[0]))return null;const li=x=>[...x.matchAll(/<li class="mcq-o( mcq-ok)?">([\s\S]*?)<\/li>/g)];
 const a=li(f[1]);return{q:f[0].slice(0,f[0].indexOf('<ol class="mcq"')),opts:li(f[0]).map(m=>m[2]),right:a.findIndex(m=>m[1]),extra:f[2],title:f[3]}}
function mcqFace(m,A,pi,key){if(MQ.key!=key){MQ.key=key;MQ.picked=null;MQ.order=m.opts.map((_,i)=>i);for(let i=MQ.order.length-1;i>0;i--){const j=Math.random()*(i+1)|0;[MQ.order[i],MQ.order[j]]=[MQ.order[j],MQ.order[i]]}}
 const ops=MQ.order.map(i=>A?`<label data-o="${i}" class="${i==m.right?(MQ.picked==i?'correct-answer':'highlight-correct'):MQ.picked==i?'highlight-picked highlight-wrong':''}">${m.opts[i]}</label>`
  :`<input type="radio" id="opt${i}" name="mcq" value="${i}"><label for="opt${i}" data-o="${i}">${m.opts[i]}</label>`).join('');
 return deck(pi)+HR+ttl(m.title)+`<div class="prettify-field prettify-field--front mcq-qf"><div class="center-text"><div class="prettify-field center-text zoomable-image">${m.q}</div></div></div>`+HR   /* the question, a divider, then the options */
  +`<div class="prettify-field prettify-field--front mcq-of"><div class="center-text"><div class="prettify-field option-group center-text">${ops}</div></div></div>`
}
/* the explanation: its own small card under the MCQ card on the answer side (Settings › Reviewing › Show explanation) */
/* Material You: the phone's colours as card variables (html[data-theme=you]), sent by the app */
function youVars(css){let s=document.getElementById('you-vars');if(!s){s=document.createElement('style');s.id='you-vars';document.head.appendChild(s)}s.textContent=css}
let EXIN=false,TURN=false;   /* TURN: the reviewer can turn an MCQ answer over to its explanation (Hide explanation) */
/* the answer card with the explanation on its back: both sides share one grid cell, so they're the same height (the taller one, up to the
   card's cap) and each scrolls on its own */
const mcqTurn=(fr,m)=>`<div class="mcq-flip"><div class="mcq-rot"><div class="mcq-side mcq-front">${fr}</div><div class="mcq-side mcq-back"><div class="prettify-flashcard mcq-exb"><div class="prettify-field center-text extra">${m.extra}</div></div></div></div></div>`;   /* feed posts: the explanation inside the MCQ card, as before */
/* a post's explanation: under a divider, collapsed to a few lines; the chevron shows the rest or hides it */
const mcqIn=m=>m.extra&&SET.expl?`<hr class="prettify-divider prettify-divider--answer"><div class="mcq-col"><div class="prettify-field center-text extra mcq-colb">${m.extra}</div><button class="mcq-chev" onclick="event.stopPropagation();this.parentNode.classList.toggle('open')" aria-label="Show more"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg></button></div>`:'';
const mcqEx=m=>m.extra&&SET.expl?`<div class="prettify-flashcard mcq-exc"><div class="prettify-field center-text extra">${m.extra}</div></div>`:'';
function face(A){const{n,o}=cur,[,k,pi]=n,f=n[4].map(trimF);let h;
const mt=k=='b'&&matchOf(f);if(mt){const fr=`<div class="prettify-flashcard mcq-card mt-card">${matchFace(mt,A,pi,n[0]+':'+o)}${A&&EXIN?mcqIn(mt):''}${tags(n)}</div>`;
 if(A&&!EXIN&&TURN&&SET.hidex&&SET.expl&&mt.extra)return mcqTurn(fr,mt);return fr+(A&&!EXIN?mcqEx(mt):'')}
const mq=k=='b'&&mcqOf(f);if(mq){const fr=`<div class="prettify-flashcard mcq-card">${mcqFace(mq,A,pi,n[0]+':'+o)}${A&&EXIN?mcqIn(mq):''}${tags(n)}</div>`;
 if(A&&!EXIN&&TURN&&SET.hidex&&SET.expl&&mq.extra)return mcqTurn(fr,mq);return fr+(A&&!EXIN?mcqEx(mq):'')}
if(k=='b'){const[fr,bk,ex,ti,so,ht]=f,sr=SET.source?so:'',q=o?bk:fr;
h=deck(pi)+HR+(A?ttl(ti)+(f[6]=='ioe'?'':fld('front',q)+HR)+fld('back',o?fr:bk)+(ex?HR+fld('back',ex,'center-text extra'):'')+(!o&&sr?DV+fld('front zoomable-image',sr,'center-text extra'):'')+tags(n)
:(o?'':ttl(ti))+fld('front',q)+(o?'':tags(n)))}
else if(k=='c'){const[tx,ex,ti,so,ht,lf,sq,ha]=f,sr=SET.source?so:'',L=!lf,sn=(sq||'').trim()?(Number(sq)||1):0,sb=sn&&!A?`<div class="sequentialCloze" data-n="${sn}">`:'',se=sn&&!A?'</div>':'',ab=A&&ha&&[...tx.matchAll(/\{\{c(\d+)::/g)].some(m=>+m[1]!=o+1)?'<div class="center-text"><button id="allClozeBtn">Reveal all clozes</button></div>':'',w=x=>`<div class="prettify-field prettify-field--front zoomable-image cloze-extra">${L?'<div class="center-text extra">':''}${x}${L?'</div>':''}</div>`;
h=deck(pi)+DV+`<div class="prettify-field prettify-field--front">${sb}${L?'<div class="center-text">':''}${ti?`<h3 class="title">${ti}</h3><div class="shift-right">`:''}${cloze(tx,o+1,A,ha)}${ab}${ti?'</div>':''}${L?'</div>':''}${se}</div>`
+(A&&ex?DV+w(ex):'')+(A&&sr?DV+w(sr):'')+tags(n)}
else{const[oc,hd,im,ft,...r]=f,x=r.filter(Boolean),g=y=>HR+fld('front extra',`<div id="io-footer">${y}</div>`),px=oc.search(IOR)>=0,io=`<div class="io-c" id="image-occlusion-container">${im}<svg ${px?'class="io-px" viewBox="0 0 0 0"':'viewBox="0 0 1 1"'} preserveAspectRatio="none">${px?rects(oc,A):shapes(oc,o+1,A)}</svg></div>`;
h=deck(pi)+HR+(hd?fld('front',`<div id="io-header">${hd}</div>`)+HR:'')+fld('front',px&&!A?`<div class="sequentialCloze" data-n="1">${io}</div>`:io)+(ft?g(ft):'')+(A?x.slice(0,5).map(g).join(''):'')+tags(n)}
return `<div class="prettify-flashcard${k=='c'?' prettify-flashcard-front':''}">${h}</div>`}
const medURL=k=>'/media/'+encodeURIComponent(k);
/* an element's deck images: <img data-m> gets its src from the app's media folder */
function imgs(el){el.querySelectorAll('img[data-m]').forEach(i=>{i.src=medURL(i.dataset.m)});mtInit(el)}
/* an image that won't load (missing from the deck's media, or a dead link): a small rounded box in the page's colour with a stop sign
   in the card's colour. --pbg: the page colour (the feed sets --bgc; the reviewer's is the body's own background) */
const BADIMG='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2.586 16.726A2 2 0 0 1 2 15.312V8.688a2 2 0 0 1 .586-1.414l4.688-4.688A2 2 0 0 1 8.688 2h6.624a2 2 0 0 1 1.414.586l4.688 4.688A2 2 0 0 1 22 8.688v6.624a2 2 0 0 1-.586 1.414l-4.688 4.688a2 2 0 0 1-1.414.586H8.688a2 2 0 0 1-1.414-.586z"/><path d="M8 12h8"/></svg>';
addEventListener('error',e=>{const i=e.target;if(!i||i.tagName!='IMG'||!i.closest('.prettify-flashcard'))return;
 document.documentElement.style.setProperty('--pbg',getComputedStyle(document.body).backgroundColor);
 const b=document.createElement('span');b.className='img-bad';b.innerHTML=BADIMG;i.replaceWith(b)},true);

/* ---- Match (the importer's Match type: field 0 the question with its two columns as table.mt-src, field 1 the right pairs, field 6
   'match'). drawn as a wire board: two columns of option buttons (the MCQ options' style), each left item ends in a coloured plug,
   each right item starts with a socket. on the question side the reviewer taps an item, then one in the other column, to wire them. once every left item is wired: all right → the card turns to the answer (App.pick: right only if no wrong try);
   otherwise the board shakes and the wires stay to be fixed. the answer side shows the right wiring */
const MTC=['red','blue','yellow','pink','green','peach','mauve','teal'];
const MTK='(?:[0-9]{1,2}|ix|iv|vi{0,3}|i{1,3}|x|[A-Za-z])',MTP=new RegExp('(?<![\\w])('+MTK+')\\s*[-–:→]\\s*('+MTK+')(?![\\w])','g');
function matchOf(f){if(f[6]!='match')return null;const d=document.createElement('div');d.innerHTML=f[0];const t=d.querySelector('table.mt-src');if(!t)return null;
 const L=[],R=[];t.querySelectorAll('tr').forEach(tr=>{const c=tr.children;if(c[0]&&c[0].dataset.k)L.push({k:c[0].dataset.k,h:c[0].innerHTML});if(c[1]&&c[1].dataset.k)R.push({k:c[1].dataset.k,h:c[1].innerHTML})});
 t.remove();const pr={};for(const m of String(f[1]||'').replace(/<[^>]*>/g,' ').matchAll(MTP))pr[m[1]]=m[2];
 if(!L.length||!R.length||L.some(x=>!(x.k in pr)))return null;return{q:d.innerHTML,L,R,pr,extra:f[2],title:f[3]}}
const MT={key:'',con:{},bad:false,sel:null};   /* sel: the selected item {b, s: 'l'|'r', k} */
function matchFace(m,A,pi,key){if(MT.key!=key){MT.key=key;MT.con={};MT.bad=false;MT.sel=null}
 const col=(s,l)=>l.map((x,i)=>`<div class="mt-it mt-${s}" data-k="${x.k}"${s=='l'?` style="--wc:rgb(var(--ctp-${MTC[i%MTC.length]}))"`:''}><span class="mt-k">${x.k}</span><div class="mt-t">${x.h}</div><i class="mt-n"></i></div>`).join('');
 return deck(pi)+HR+ttl(m.title)+`<div class="prettify-field prettify-field--front mcq-qf"><div class="center-text"><div class="prettify-field center-text zoomable-image">${m.q}</div></div></div>`+HR
  +`<div class="mt${A?' mt-done':''}" data-key="${key}"${A?` data-pr='${JSON.stringify(m.pr).replace(/'/g,'&#39;')}'`:''}><svg class="mt-w"></svg><div class="mt-col">${col('l',m.L)}</div><div class="mt-col">${col('r',m.R)}</div></div>`}
const MTRO=new ResizeObserver(es=>es.forEach(e=>mtDraw(e.target)));
function mtInit(el){(el.matches&&el.matches('.mt')?[el]:[...el.querySelectorAll('.mt')]).forEach(b=>{MTRO.observe(b);requestAnimationFrame(()=>mtDraw(b))})}
/* the board's wiring: the answer side's is the right one (data-pr); the question side's is MT.con */
const mtCon=b=>b.classList.contains('mt-done')?JSON.parse(b.dataset.pr||'{}'):MT.con;
function mtDraw(b){const svg=b.querySelector('.mt-w');if(!svg)return;svg.setAttribute('viewBox',`0 0 ${b.offsetWidth} ${b.offsetHeight}`);
 /* positions from the layout (offsets), not the screen: the answer side is drawn while it's still turned away mid-flip */
 const pt=e=>{let x=e.offsetWidth/2,y=e.offsetHeight/2,n=e;while(n&&n!==b){x+=n.offsetLeft;y+=n.offsetTop;n=n.offsetParent}return[x,y]};
 const it=(s,k)=>b.querySelector(`.mt-${s}[data-k="${CSS.escape(k)}"]`);const con=mtCon(b);let h='';
 b.querySelectorAll('.mt-r').forEach(x=>{x.classList.remove('on');x.style.removeProperty('--wc')});b.querySelectorAll('.mt-l').forEach(x=>x.classList.toggle('on',x.dataset.k in con));
 const sl=!b.classList.contains('mt-done')&&MT.sel;b.querySelectorAll('.mt-it').forEach(x=>x.classList.toggle('sel',!!sl&&sl.b===b&&x.classList.contains('mt-'+sl.s)&&x.dataset.k==sl.k));
 const wire=(l,[x2,y2])=>{const[x1,y1]=pt(l.querySelector('.mt-n')),c=l.style.getPropertyValue('--wc');
  return `<path d="M${x1} ${y1}L${x2} ${y2}" style="stroke:${c}"/>`};   /* a straight line; style, not the stroke attribute: the colour is a var() */   /* style, not the stroke attribute: the colour is a var() */
 for(const k in con){const l=it('l',k),r=it('r',con[k]);if(!l||!r)continue;r.classList.add('on');r.style.setProperty('--wc',l.style.getPropertyValue('--wc'));   /* a pair shares its wire's colour */h+=wire(l,pt(r.querySelector('.mt-n')))}
 svg.innerHTML=h}
/* the question side's taps: tap an item to select it, then an item in the other column to wire them (replacing either one's wire).
   a tap on anything else (the same column, the board, the rest of the card) just drops the selection; a tap on the same column's
   other item selects that one instead */
document.addEventListener('click',e=>{if(MT.sel==null)return;const b=MT.sel.b;if(!b||!b.isConnected){MT.sel=null;return}
 const it=e.target.closest&&e.target.closest('.mt-it');if(it&&b.contains(it))return;MT.sel=null;mtDraw(b)},true);
document.addEventListener('click',e=>{const b=e.target.closest&&e.target.closest('.mt:not(.mt-done)');if(!b)return;const it=e.target.closest('.mt-it');if(!it)return;
 e.preventDefault();e.stopPropagation();const s=it.classList.contains('mt-l')?'l':'r',k=it.dataset.k,c=MT.sel;
 if(c&&c.s!=s){const lk=s=='l'?k:c.k,rk=s=='l'?c.k:k;if(MT.con[lk]==rk){delete MT.con[lk];MT.sel=null;mtDraw(b)}else mtWire(b,lk,rk);return}   /* the same pair again: unwired */
 MT.sel=c&&c.k==k?null:{b,s,k};mtDraw(b)},true);
function mtWire(b,lk,rk){for(const k in MT.con)if(MT.con[k]==rk)delete MT.con[k];MT.con[lk]=rk;MT.sel=null;mtDraw(b);   /* either end's old wire goes */
 const m=cur&&matchOf(cur.n[4].map(trimF));if(!m||m.L.some(x=>!(x.k in MT.con)))return;
 if(m.L.every(x=>MT.con[x.k]==m.pr[x.k])){b.classList.add('mt-ok');setTimeout(()=>{if(MT.key==b.dataset.key&&window.mtDone)mtDone(!MT.bad)},500)}
 else{MT.bad=true;b.classList.remove('mt-no');void b.offsetWidth;b.classList.add('mt-no')}}

/* tags: the fade at an edge shows while there's more past it */
function tagFade(t){const o=t.scrollWidth-t.clientWidth;t.style.setProperty('--fl',o>2&&t.scrollLeft>2?'22px':'0px');t.style.setProperty('--fr',o>2&&t.scrollLeft<o-2?'22px':'0px')}
document.addEventListener('scroll',e=>{const t=e.target;if(t.classList&&t.classList.contains('prettify-tags'))tagFade(t)},true);
{let q=0;new MutationObserver(()=>{if(q)return;q=requestAnimationFrame(()=>{q=0;document.querySelectorAll('.prettify-tags').forEach(tagFade)})}).observe(document.documentElement,{childList:true,subtree:true})}
addEventListener('resize',()=>document.querySelectorAll('.prettify-tags').forEach(tagFade));
