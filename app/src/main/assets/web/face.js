/* medslave · face.js: a card's HTML (basic, cloze, image occlusion, IO one by one, MathJax, trimmed blank lines), shared by
   card.html (the reviewer) and feed.html (Reels Mode's home feed). face(A) draws cur = {n, o}; A = the answer side */
const $=s=>document.querySelector(s),HR='<hr class="prettify-divider prettify-divider--answer">',DV='<div class="prettify-divider prettify-divider--answer"></div>';
let DATA={paths:[]},SET={source:false,expl:true,tags:false},cur=null,stage=0,FL=false;
const deck=pi=>`<div class="prettify-deck">${(DATA.paths[pi]||'').split(' › ').map(x=>`<span class="prettify-subdeck">${x}</span>`).join('&nbsp;/&nbsp;')}</div>`;
/* a card's tags at its bottom (Settings › Reviewing › Show tags): each tag's last part, as on the site's cards */
const tags=n=>SET.tags&&n[5]&&n[5].length?`<div class="prettify-tags">${n[5].map(t=>{const l=String(t).split('::').filter(Boolean).pop().replace(/_/g,' ').trim();return `<span class="prettify-tag">${l.replace(/</g,'&lt;')}</span>`}).join('')}</div>`:'';
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
let EXIN=false;   /* feed posts: the explanation inside the MCQ card, as before */
/* a post's explanation: under a divider, collapsed to a few lines; the chevron shows the rest or hides it */
const mcqIn=m=>m.extra&&SET.expl?`<hr class="prettify-divider prettify-divider--answer"><div class="mcq-col"><div class="prettify-field center-text extra mcq-colb">${m.extra}</div><button class="mcq-chev" onclick="event.stopPropagation();this.parentNode.classList.toggle('open')" aria-label="Show more"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg></button></div>`:'';
const mcqEx=m=>m.extra&&SET.expl?`<div class="prettify-flashcard mcq-exc"><div class="prettify-field center-text extra">${m.extra}</div></div>`:'';
function face(A){const{n,o}=cur,[,k,pi]=n,f=n[4].map(trimF);let h;
const mq=k=='b'&&mcqOf(f);if(mq)return `<div class="prettify-flashcard mcq-card">${mcqFace(mq,A,pi,n[0]+':'+o)}${A&&EXIN?mcqIn(mq):''}${tags(n)}</div>`+(A&&!EXIN?mcqEx(mq):'');
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
function imgs(el){el.querySelectorAll('img[data-m]').forEach(i=>{i.src=medURL(i.dataset.m)})}
