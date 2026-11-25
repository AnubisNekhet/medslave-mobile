/* medslave · feed.js: Reels Mode's home feed. The app (Feed.kt) makes the accounts, posts and stories from the deck and hands
   them over through R.*; this draws them. Posts and stories show cards' answers (face.js).
   App.more(): near the end of the posts, the app sends more (R.posts). App.com(handle, n) / App.share(): the app's comment and
   share sheets. App.story(open): the story viewer opened or closed (the app hides its bottom bar). App.seen(group). App.imp().
   App.settings() / App.credits(): the gear in the top right, and the logo (as on the Menu) */
const A_=window.App||{more(){},com(){},share(){},story(){},seen(){},imp(){},settings(){},credits(){}};
const F={acc:[],posts:[],groups:[],wait:false};
function fitX(){}   /* face.js calls it after MathJax; the feed has nothing to refit */
const ESC=s=>String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;');
const SVG=(p,a='')=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"${a}>${p}</svg>`;
const ICN={heart:'<path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/>',
 com:'<path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z"/>',send:'<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>',save:'<path d="m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z"/>'};
const fmt=n=>n>=1e6?(n/1e6).toFixed(1).replace(/\.0$/,'')+'M':n>=1e4?Math.round(n/1e3)+'K':n>=1e3?(n/1e3).toFixed(1).replace(/\.0$/,'')+'K':''+n;
const avatar=a=>`<span class="f-av" style="background:${a.c}">${SVG(a.ic)}</span>`;
/** a story: the card's answer side exactly as the reviewer draws it */
function reviewHTML(n,o){cur={n,o};stage=1;FL=false;return `<div class="wrap"><div class="tinder-swipe tinder-swipe--answer">${face(true)}</div></div>`}
/** a post: the card's answer side without its deck path */
function answerHTML(n,o){cur={n,o};stage=1;FL=false;return face(true).replace(/^(<div class="prettify-flashcard[^"]*">)<div class="prettify-deck">[\s\S]*?<\/div>(?:<hr[^>]*>|<div class="prettify-divider[^"]*"><\/div>)/,'$1')}   /* no deck path in the feed */
/* after new cards are on the page: images, MathJax, occlusion boxes, and whether a post needs 'more' */
function settle(el){imgs(el);mj(el);el.querySelectorAll('.f-pb').forEach(clip)}
function clip(b){const c=b.querySelector('.prettify-flashcard');b.classList.toggle('clip',!!c&&c.scrollHeight>c.clientHeight+2)}
document.addEventListener('load',e=>{if(e.target.tagName!='IMG')return;ioBox(e.target);const b=e.target.closest('.f-pb');if(b)clip(b)},true);

/* ---- posts */
function postHTML(p,i){const a=F.acc[p.a];return `<article class="f-post" data-i="${i}">
<header class="f-ph">${avatar(a)}<span class="f-pn"><b>${ESC(a.h)}</b></span></header>
<div class="f-pb f-load">${skel()}</div>
<div class="f-pa"><button class="f-lk" aria-label="Like">${SVG(ICN.heart)}<span class="f-lkn">${fmt(p.likes)}</span></button><button class="f-cm" aria-label="Comments">${SVG(ICN.com)}<span>${fmt(p.coms)}</span></button><button class="f-sh" aria-label="Share">${SVG(ICN.send)}</button><button class="f-sv" aria-label="Save">${SVG(ICN.save)}</button></div>
<div class="f-pc"><b>${ESC(a.h)}</b> ${ESC(p.cap).replace(/#[^\s#]+/g,'<span class="f-tag">$&</span>')}</div>
<div class="f-pv f-cm">View all ${fmt(p.coms)} comments</div>
${p.cm.map(([u,t])=>`<div class="f-pc"><b>${ESC(u)}</b> ${ESC(t)}</div>`).join('')}
<div class="f-pt">${ESC(p.t)}</div></article>`}
/* ---- the heatmap post: always the first post, from daily.heatmap. answers revealed per day (the app's medslave:activity), one
   block per month, Monday-first weeks, today at the right end; a tap on a day shows its count. caption: today and the streak */
const HACC={h:'daily.heatmap',c:'#a6e3a1',ic:'<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/><path d="M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01M16 18h.01"/>'};
const HM=['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'],dk=d=>d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
F.hp={likes:0,coms:0,liked:false};let HJ='';
function heatGrid(m){const t=new Date();t.setHours(0,0,0,0);const mx=Math.max(0,...Object.values(m).map(Number).filter(isFinite));let o='';
 for(let i=11;i>=0;i--){const f=new Date(t.getFullYear(),t.getMonth()-i,1),y=f.getFullYear(),mo=f.getMonth(),last=i?new Date(y,mo+1,0).getDate():t.getDate();let g='<i class="f"></i>'.repeat((f.getDay()+6)%7);
  for(let d=1;d<=last;d++){const k=dk(new Date(y,mo,d)),c=+m[k]||0,l=c&&mx?Math.max(1,Math.ceil(4*c/mx)):0;g+=`<i class="l${l}${!i&&d==last?' t':''}" data-k="${k}" data-c="${c}"></i>`}
  o+=`<div class="f-mb"><b>${HM[mo]}${mo?'':' '+y}</b><div class="f-hg">${g}</div></div>`}return o}
function heatCap(m){const t=new Date();let n=+m[dk(t)]||0,s=0;const d=new Date(t);if(!n)d.setDate(d.getDate()-1);while(+m[dk(d)]>0){s++;d.setDate(d.getDate()-1)}
 return (n?`${n} card${n==1?'':'s'} today`:'no cards yet today')+(s>1?` · ${s}-day streak 🔥`:'')+' #consistency #dailygrind'}
function heatPost(m){const p=F.hp,a=HACC;if(!p.likes){p.likes=800+Math.random()*40000|0;p.coms=20+Math.random()*900|0}
 return `<article class="f-post" data-i="h"><header class="f-ph">${avatar(a)}<span class="f-pn"><b>${a.h}</b></span></header>
<div class="f-hmb"><div class="f-hm">${heatGrid(m)}</div><div class="f-hmt"></div></div>
<div class="f-pa"><button class="f-lk${p.liked?' on':''}" aria-label="Like">${SVG(ICN.heart)}<span class="f-lkn">${fmt(p.likes+(p.liked?1:0))}</span></button><button class="f-cm" aria-label="Comments">${SVG(ICN.com)}<span>${fmt(p.coms)}</span></button><button class="f-sh" aria-label="Share">${SVG(ICN.send)}</button><button class="f-sv" aria-label="Save">${SVG(ICN.save)}</button></div>
<div class="f-pc"><b>${a.h}</b> ${ESC(heatCap(m)).replace(/#[^\s#]+/g,'<span class="f-tag">$&</span>')}</div><div class="f-pt">today</div></article>`}
function heat(m){const j=JSON.stringify(m||{});if(j==HJ&&$('[data-i="h"]'))return;HJ=j;const old=$('[data-i="h"]'),t=document.createElement('div');t.innerHTML=heatPost(m||{});const el=t.firstElementChild;
 old?old.replaceWith(el):$('#posts').prepend(el);const h=el.querySelector('.f-hm');h.scrollLeft=h.scrollWidth}
$('#posts').addEventListener('click',e=>{const c=e.target.closest('.f-hg i[data-k]');if(!c)return;const d=new Date(c.dataset.k+'T00:00'),n=+c.dataset.c;
 c.closest('.f-post').querySelector('.f-hmt').textContent=d.toLocaleDateString(undefined,{weekday:'short',day:'numeric',month:'short',year:'numeric'})+' · '+n+' card'+(n==1?'':'s')+' done'});
/* "online" loading: a post's card and a story start as a shimmering placeholder and turn up after a believable delay, mostly
   .2–.9s, now and then up to ~2s; more posts take a moment to arrive too. it's all local, it just shouldn't look it */
const LAG=()=>Math.random()<.15?900+Math.random()*1300:200+Math.random()*700;
function skel(){const w=[[86,64,72],[78,92,58,66],[70,88],[90,60,80,50]][Math.random()*4|0];
 return `<div class="f-sk" style="min-height:${120+Math.random()*110|0}px">${w.map(x=>`<i style="width:${x}%"></i>`).join('')}</div>`}
function arrive(b){if(b.dataset.t)return;b.dataset.t=1;setTimeout(()=>{const el=b.closest('.f-post'),p=F.posts[+el.dataset.i];if(!p)return;
 b.classList.remove('f-load');b.innerHTML=answerHTML(p.n,p.o)+'<div class="f-pmore">more</div>';b.classList.add('f-in');settle(el)},LAG())}
const NEAR=new IntersectionObserver(es=>es.forEach(e=>{if(e.isIntersecting){NEAR.unobserve(e.target);arrive(e.target)}}),{root:$('#feed'),rootMargin:'300px 0px'});
function addPosts(l){const box=$('#posts'),at=F.posts.length;F.posts.push(...l);$('.f-loadmore')?.remove();
 const t=document.createElement('div');t.innerHTML=l.map((p,k)=>postHTML(p,at+k)).join('')+(l.length?'<div class="f-loadmore"><i class="lg"></i></div>':'');
 const els=[...t.children];els.forEach(e=>box.appendChild(e));els.forEach(e=>{const b=e.querySelector&&e.querySelector('.f-pb.f-load');if(b)NEAR.observe(b)});F.wait=false}
const RM=()=>matchMedia('(prefers-reduced-motion:reduce)').matches;
/** restarts a button's one-shot animation (.go) */
function kick(b){b.classList.remove('go');void b.offsetWidth;b.classList.add('go')}
function like(el,p,v){p.liked=v;const b=el.querySelector('.f-lk');b.classList.toggle('on',v);b.classList.remove('pop','unpop');void b.offsetWidth;b.classList.add(v?'pop':'unpop');el.querySelector('.f-lkn').textContent=fmt(p.likes+(v?1:0))}
let LT=0,LX=null;
$('#posts').addEventListener('click',e=>{const el=e.target.closest('.f-post');if(!el)return;const hp=el.dataset.i=='h',p=hp?F.hp:F.posts[+el.dataset.i],a=hp?HACC:F.acc[p.a];
 if(e.target.closest('.f-lk'))return like(el,p,!p.liked);
 const cm=e.target.closest('.f-pa .f-cm'),sh=e.target.closest('.f-sh'),sv=e.target.closest('.f-sv');
 if(cm){kick(cm);return setTimeout(()=>A_.com(a.h,p.coms),RM()?0:160)}
 if(e.target.closest('.f-pv.f-cm'))return A_.com(a.h,p.coms);
 if(sh){kick(sh);return setTimeout(()=>A_.share(),RM()?0:260)}
 if(sv){sv.classList.toggle('on');return kick(sv)}
 const b=e.target.closest('.f-pb');if(!b)return;
 if(e.target.closest('.f-pmore')){b.classList.add('open');return}
 /* a double tap on the card likes it: a heart bursts in the middle */
 const now=Date.now();if(now-LT<320&&LX===el){LT=0;const h=document.createElement('i');h.className='f-burst';h.innerHTML=SVG(ICN.heart);b.appendChild(h);setTimeout(()=>h.remove(),900);if(!p.liked)like(el,p,true)}else{LT=now;LX=el}});
const FD=$('#feed');FD.addEventListener('scroll',()=>{if(!F.wait&&F.posts.length&&FD.clientHeight+FD.scrollTop>FD.scrollHeight-FD.clientHeight*1.5){F.wait=true;F.asked=Date.now();A_.more()}},{passive:true});
$('#eimp').onclick=()=>A_.imp();$('#wm').onclick=()=>A_.credits();$('#gear').onclick=()=>A_.settings();

/* ---- stories: the row of accounts, and the viewer (one page per account, swiped sideways; tap right / left for the next /
   previous story, hold to pause; each story runs DUR ms, then the next, then the next account) */
function rings(){$('#stories').innerHTML=F.groups.map((g,i)=>{const a=F.acc[g.a];return `<button class="f-st" data-g="${i}"><span class="f-ring${g.seen?' seen':''}">${avatar(a)}</span><span class="f-stn">${ESC(a.h)}</span></button>`}).join('');
 $('#stories').hidden=!F.groups.length}
$('#stories').addEventListener('click',e=>{const b=e.target.closest('.f-st');if(b)svOpen(+b.dataset.g)});
/* the circle of story g, as the point a story grows from / shrinks into */
function ringAt(g){const r=$(`.f-st[data-g="${g}"] .f-ring`);if(!r)return null;const b=r.getBoundingClientRect();return b.bottom>0&&b.top<innerHeight?b:null}
const SV={g:0,i:0,el:0,last:0,hold:false,raf:0,on:false,DUR:8000};
const page=g=>$('#svp').children[g];
function svBuild(){$('#svp').innerHTML=F.groups.map(g=>{const a=F.acc[g.a];return `<section class="f-sg"><div class="f-bars">${g.items.map(()=>'<span class="f-bar"><i></i></span>').join('')}</div>
<div class="f-sh">${avatar(a)}<b>${ESC(a.h)}</b><small>${ESC(g.t)}</small><button class="f-sx" aria-label="Close">×</button></div><div class="f-sc"></div></section>`}).join('')}
function svShow(g,i){const G=F.groups[g];if(!G)return svClose();SV.g=g;SV.i=i;SV.el=0;
 [...$('#svp').children].forEach((s,k)=>{if(k!=g)s.querySelector('.f-sc').innerHTML=''});
 const s=page(g);s.querySelectorAll('.f-bar i').forEach((b,k)=>b.style.width=k<i?'100%':'0');
 const c=s.querySelector('.f-sc');c.innerHTML='<div class="f-si">'+reviewHTML(G.items[i].n,G.items[i].o)+'</div>';c.scrollTop=0;imgs(c);mj(c);
 if(!G.seen){G.seen=true;A_.seen(g)}}
function svTick(t){if(!SV.on)return;const dt=SV.last?t-SV.last:0;SV.last=t;if(!SV.hold)SV.el+=dt;
 const b=page(SV.g)?.querySelectorAll('.f-bar i')[SV.i];if(b)b.style.width=Math.min(100,SV.el/SV.DUR*100)+'%';
 if(SV.el>=SV.DUR)svNext();SV.raf=requestAnimationFrame(svTick)}
function svGo(g){const p=$('#svp');p.scrollTo({left:g*p.clientWidth,behavior:'smooth'});svShow(g,0)}
function svNext(){const G=F.groups[SV.g];if(SV.i<G.items.length-1)svShow(SV.g,SV.i+1);else if(SV.g<F.groups.length-1)svGo(SV.g+1);else svClose()}
function svPrev(){if(SV.i>0)svShow(SV.g,SV.i-1);else if(SV.g>0)svGo(SV.g-1);else svShow(SV.g,0)}
function svOpen(g){svBuild();const sv=$('#sv'),r=ringAt(g);sv.classList.add('on');SV.on=true;SV.last=0;A_.story(true);
 if(r&&!RM()){sv.style.transformOrigin=`${r.left+r.width/2}px ${r.top+r.height/2}px`;sv.animate([{transform:`scale(${r.width/innerWidth})`,opacity:.3,borderRadius:'50%'},{transform:'none',opacity:1,borderRadius:'0'}],{duration:320,easing:'cubic-bezier(.2,.8,.2,1)'})}
const p=$('#svp');p.scrollLeft=g*p.clientWidth;svShow(g,0);cancelAnimationFrame(SV.raf);SV.raf=requestAnimationFrame(svTick)}
function svClose(now){if(!SV.on)return;SV.on=false;cancelAnimationFrame(SV.raf);const sv=$('#sv'),r=!now&&!RM()&&ringAt(SV.g);
 if(r){A_.story(false);sv.style.transformOrigin=`${r.left+r.width/2}px ${r.top+r.height/2}px`;sv.animate([{transform:'none',opacity:1,borderRadius:'0'},{transform:`scale(${r.width/innerWidth})`,opacity:0,borderRadius:'50%'}],{duration:240,easing:'cubic-bezier(.4,0,.6,1)'}).onfinish=()=>{if(!SV.on){sv.classList.remove('on');$('#svp').innerHTML=''}};if(F.next){F.groups=F.next;F.next=null}rings();return}
 $('#sv').classList.remove('on');$('#svp').innerHTML='';A_.story(false);if(F.next){F.groups=F.next;F.next=null}rings()}
/* swiping to another account starts it at its first story */
let SVS=0;$('#svp').addEventListener('scroll',()=>{clearTimeout(SVS);SVS=setTimeout(()=>{const p=$('#svp'),g=Math.round(p.scrollLeft/p.clientWidth);if(SV.on&&g!=SV.g)svShow(g,0)},120)},{passive:true});
/* a tap: right 70% = next, left 30% = previous; holding pauses */
let PD=null,HT=0;
$('#svp').addEventListener('pointerdown',e=>{if(e.target.closest('.f-sx'))return;PD={x:e.clientX,y:e.clientY,t:Date.now()};HT=setTimeout(()=>{SV.hold=true},180)});
$('#svp').addEventListener('pointerup',e=>{clearTimeout(HT);const d=PD;PD=null;const held=SV.hold;SV.hold=false;
 if(e.target.closest('.f-sx'))return svClose();if(!d||held||Math.hypot(e.clientX-d.x,e.clientY-d.y)>10)return;
 e.clientX<innerWidth*.3?svPrev():svNext()});
$('#svp').addEventListener('pointercancel',()=>{clearTimeout(HT);PD=null;SV.hold=false});

/* ---- the app's calls */
const R={
 theme(t,dark){document.documentElement.dataset.theme=t;document.body.classList.toggle('night_mode',dark);document.body.classList.toggle('nightMode',dark);
  document.body.style.setProperty('--bgc',getComputedStyle(document.body).backgroundColor)},
 feed(d){DATA.paths=d.paths;SET.source=!!d.source;F.acc=d.acc;F.posts=[];F.groups=d.groups;$('#posts').innerHTML='';svClose();rings();
  $('#empty').hidden=!!d.acc.length;HJ='';heat(d.act);addPosts(d.posts);FD.scrollTop=0},
 heat(m){heat(m)},
 insets(t,b){const s=document.documentElement.style;s.setProperty('--st',t+'px');s.setProperty('--sb',b+'px')},   /* status / navigation bar heights: the page runs behind the status bar */
 posts(l){setTimeout(()=>addPosts(l),Math.max(0,500+Math.random()*900-(Date.now()-(F.asked||0))))},
 stories(g){if(SV.on){F.next=g;return}F.groups=g;rings()},   /* new stories wait while the viewer is open */
 close(){svClose()},
 top(i){if(SV.on&&i)svClose(true);FD.scrollTo({top:0,behavior:i?'instant':'smooth'})}};
