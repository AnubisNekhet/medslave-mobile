/* medslave · feed.js: Reels Mode's home feed. The app (Feed.kt) makes the accounts, posts and stories from the deck and hands
   them over through R.*; this draws them. Posts and stories show cards' answers (face.js).
   App.more(): near the end of the posts, the app sends more (R.posts). App.com(handle, n) / App.share(): the app's comment and
   share sheets. App.story(open): the story viewer opened or closed (the app hides its bottom bar). App.seen(group). App.imp() */
const A_=window.App||{more(){},com(){},share(){},story(){},seen(){},imp(){}};
const F={acc:[],posts:[],groups:[],wait:false};
function fitX(){}   /* face.js calls it after MathJax; the feed has nothing to refit */
const ESC=s=>String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;');
const SVG=(p,a='')=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"${a}>${p}</svg>`;
const ICN={heart:'<path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/>',
 com:'<path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z"/>',send:'<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>',save:'<path d="m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z"/>'};
const fmt=n=>n>=1e6?(n/1e6).toFixed(1).replace(/\.0$/,'')+'M':n>=1e4?Math.round(n/1e3)+'K':n>=1e3?(n/1e3).toFixed(1).replace(/\.0$/,'')+'K':''+n;
const avatar=a=>`<span class="av" style="background:${a.c}">${SVG(a.ic)}</span>`;
/** a card's answer side, as the reviewer shows it */
function answerHTML(n,o){cur={n,o};stage=1;FL=false;return face(true)}
/* after new cards are on the page: images, MathJax, occlusion boxes, and whether a post needs 'more' */
function settle(el){imgs(el);mj(el);el.querySelectorAll('.pb').forEach(clip)}
function clip(b){const c=b.querySelector('.prettify-flashcard');b.classList.toggle('clip',!!c&&c.scrollHeight>c.clientHeight+2)}
document.addEventListener('load',e=>{if(e.target.tagName!='IMG')return;ioBox(e.target);const b=e.target.closest('.pb');if(b)clip(b)},true);

/* ---- posts */
function postHTML(p,i){const a=F.acc[p.a];return `<article class="post" data-i="${i}">
<header class="ph">${avatar(a)}<span class="pn"><b>${ESC(a.h)}</b><small>${ESC(p.where)}</small></span><span class="pm">···</span></header>
<div class="pb">${answerHTML(p.n,p.o)}<div class="pmore">more</div></div>
<div class="pa"><button class="lk" aria-label="Like">${SVG(ICN.heart)}</button><button class="cm" aria-label="Comments">${SVG(ICN.com)}</button><button class="sh" aria-label="Share">${SVG(ICN.send)}</button><button class="sv" aria-label="Save">${SVG(ICN.save)}</button></div>
<div class="pl"><span class="lkn">${fmt(p.likes)}</span> likes</div>
<div class="pc"><b>${ESC(a.h)}</b> ${ESC(p.cap)}</div>
<div class="pv cm">View all ${fmt(p.coms)} comments</div>
${p.cm.map(([u,t])=>`<div class="pc"><b>${ESC(u)}</b> ${ESC(t)}</div>`).join('')}
<div class="pt">${ESC(p.t)}</div></article>`}
function addPosts(l){const box=$('#posts'),at=F.posts.length;F.posts.push(...l);$('.loadmore')?.remove();
 const t=document.createElement('div');t.innerHTML=l.map((p,k)=>postHTML(p,at+k)).join('')+(l.length?'<div class="loadmore"><i class="lg"></i></div>':'');
 const els=[...t.children];els.forEach(e=>box.appendChild(e));els.forEach(settle);F.wait=false}
function like(el,p,v){p.liked=v;const b=el.querySelector('.lk');b.classList.toggle('on',v);b.classList.remove('pop');void b.offsetWidth;if(v)b.classList.add('pop');el.querySelector('.lkn').textContent=fmt(p.likes+(v?1:0))}
let LT=0,LX=null;
$('#posts').addEventListener('click',e=>{const el=e.target.closest('.post');if(!el)return;const p=F.posts[+el.dataset.i],a=F.acc[p.a];
 if(e.target.closest('.lk'))return like(el,p,!p.liked);
 if(e.target.closest('.cm'))return A_.com(a.h,p.coms);
 if(e.target.closest('.sh'))return A_.share();
 if(e.target.closest('.sv'))return e.target.closest('.sv').classList.toggle('on');
 const b=e.target.closest('.pb');if(!b)return;
 if(e.target.closest('.pmore')){b.classList.add('open');return}
 /* a double tap on the card likes it: a heart bursts in the middle */
 const now=Date.now();if(now-LT<320&&LX===el){LT=0;const h=document.createElement('i');h.className='burst';h.innerHTML=SVG(ICN.heart);b.appendChild(h);setTimeout(()=>h.remove(),900);if(!p.liked)like(el,p,true)}else{LT=now;LX=el}});
addEventListener('scroll',()=>{if(!F.wait&&F.posts.length&&innerHeight+scrollY>document.body.scrollHeight-innerHeight*1.5){F.wait=true;A_.more()}},{passive:true});
$('#eimp').onclick=()=>A_.imp();

/* ---- stories: the row of accounts, and the viewer (one page per account, swiped sideways; tap right / left for the next /
   previous story, hold to pause; each story runs DUR ms, then the next, then the next account) */
function rings(){$('#stories').innerHTML=F.groups.map((g,i)=>{const a=F.acc[g.a];return `<button class="st" data-g="${i}"><span class="ring${g.seen?' seen':''}">${avatar(a)}</span><span class="stn">${ESC(a.h)}</span></button>`}).join('');
 $('#stories').hidden=!F.groups.length}
$('#stories').addEventListener('click',e=>{const b=e.target.closest('.st');if(b)svOpen(+b.dataset.g)});
const SV={g:0,i:0,el:0,last:0,hold:false,raf:0,on:false,DUR:8000};
const page=g=>$('#svp').children[g];
function svBuild(){$('#svp').innerHTML=F.groups.map(g=>{const a=F.acc[g.a];return `<section class="sg"><div class="bars">${g.items.map(()=>'<span class="bar"><i></i></span>').join('')}</div>
<div class="sh">${avatar(a)}<b>${ESC(a.h)}</b><small>${ESC(g.t)}</small><button class="sx" aria-label="Close">×</button></div><div class="sc"></div></section>`}).join('')}
function svShow(g,i){const G=F.groups[g];if(!G)return svClose();SV.g=g;SV.i=i;SV.el=0;
 [...$('#svp').children].forEach((s,k)=>{if(k!=g)s.querySelector('.sc').innerHTML=''});
 const s=page(g);s.querySelectorAll('.bar i').forEach((b,k)=>b.style.width=k<i?'100%':'0');
 const c=s.querySelector('.sc');c.innerHTML='<div class="si">'+answerHTML(G.items[i].n,G.items[i].o)+'</div>';c.scrollTop=0;imgs(c);mj(c);
 if(!G.seen){G.seen=true;A_.seen(g)}}
function svTick(t){if(!SV.on)return;const dt=SV.last?t-SV.last:0;SV.last=t;if(!SV.hold)SV.el+=dt;
 const b=page(SV.g)?.querySelectorAll('.bar i')[SV.i];if(b)b.style.width=Math.min(100,SV.el/SV.DUR*100)+'%';
 if(SV.el>=SV.DUR)svNext();SV.raf=requestAnimationFrame(svTick)}
function svGo(g){const p=$('#svp');p.scrollTo({left:g*p.clientWidth,behavior:'smooth'});svShow(g,0)}
function svNext(){const G=F.groups[SV.g];if(SV.i<G.items.length-1)svShow(SV.g,SV.i+1);else if(SV.g<F.groups.length-1)svGo(SV.g+1);else svClose()}
function svPrev(){if(SV.i>0)svShow(SV.g,SV.i-1);else if(SV.g>0)svGo(SV.g-1);else svShow(SV.g,0)}
function svOpen(g){svBuild();$('#sv').classList.add('on');SV.on=true;SV.last=0;A_.story(true);const p=$('#svp');p.scrollLeft=g*p.clientWidth;svShow(g,0);cancelAnimationFrame(SV.raf);SV.raf=requestAnimationFrame(svTick)}
function svClose(){if(!SV.on)return;SV.on=false;cancelAnimationFrame(SV.raf);$('#sv').classList.remove('on');$('#svp').innerHTML='';A_.story(false);if(F.next){F.groups=F.next;F.next=null}rings()}
/* swiping to another account starts it at its first story */
let SVS=0;$('#svp').addEventListener('scroll',()=>{clearTimeout(SVS);SVS=setTimeout(()=>{const p=$('#svp'),g=Math.round(p.scrollLeft/p.clientWidth);if(SV.on&&g!=SV.g)svShow(g,0)},120)},{passive:true});
/* a tap: right 70% = next, left 30% = previous; holding pauses */
let PD=null,HT=0;
$('#svp').addEventListener('pointerdown',e=>{if(e.target.closest('.sx'))return;PD={x:e.clientX,y:e.clientY,t:Date.now()};HT=setTimeout(()=>{SV.hold=true},180)});
$('#svp').addEventListener('pointerup',e=>{clearTimeout(HT);const d=PD;PD=null;const held=SV.hold;SV.hold=false;
 if(e.target.closest('.sx'))return svClose();if(!d||held||Math.hypot(e.clientX-d.x,e.clientY-d.y)>10)return;
 e.clientX<innerWidth*.3?svPrev():svNext()});
$('#svp').addEventListener('pointercancel',()=>{clearTimeout(HT);PD=null;SV.hold=false});

/* ---- the app's calls */
const R={
 theme(t,dark){document.documentElement.dataset.theme=t;document.body.classList.toggle('night_mode',dark);document.body.classList.toggle('nightMode',dark);
  document.body.style.setProperty('--bgc',getComputedStyle(document.body).backgroundColor)},
 feed(d){DATA.paths=d.paths;SET.source=!!d.source;F.acc=d.acc;F.posts=[];F.groups=d.groups;$('#posts').innerHTML='';svClose();rings();
  $('#empty').hidden=!!d.acc.length;addPosts(d.posts);scrollTo(0,0)},
 posts(l){addPosts(l)},
 stories(g){if(SV.on){F.next=g;return}F.groups=g;rings()},   /* new stories wait while the viewer is open */
 close(){svClose()},
 top(){scrollTo({top:0,behavior:'smooth'})}};
