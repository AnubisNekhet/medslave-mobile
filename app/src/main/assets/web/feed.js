/* medslave · feed.js: Reels Mode's home feed. The app (Feed.kt) makes the accounts, posts, stories and profiles from the deck and
   hands them over through R.*; this draws them. Posts and stories show cards' answers (face.js).
   App.more(): near the end of the posts, the app sends more (R.posts). App.refresh(): pulled to refresh (R.refreshed).
   App.com(handle, n) / App.share(): the app's comment and share sheets. App.story(open): the story viewer opened or closed (the app
   hides its bottom bar). App.seen(account): its story was watched. App.imp(). App.settings() / App.credits(): the gear and the logo.
   App.profile(account) → R.profile, App.pmore() → R.pposts, App.pfOpen(open), App.saved() → R.profile (saved.cards), App.save(id, o, on).
   App.play(song) / App.mute(): the song of whatever post or story is on screen, by "title · artist" ('' for none) */
const A_=window.App||{more(){},com(){},share(){},story(){},seen(){},imp(){},settings(){},credits(){},refresh(){},profile(){},pmore(){},pfOpen(){},saved(){},save(){},hide(){},play(){},mute(){}};
const F={acc:[],posts:[],groups:[],wait:false,saved:new Set()};
function fitX(){document.querySelectorAll('.f-pb').forEach(clip)}   /* face.js calls it after MathJax and after an MCQ's explanation opens: posts re-check their 'more' */
const ESC=s=>String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;');
const SVG=(p,a='')=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"${a}>${p}</svg>`;
const ICN={dots:'<circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/>',stop:'<path d="M2.586 16.726A2 2 0 0 1 2 15.312V8.688a2 2 0 0 1 .586-1.414l4.688-4.688A2 2 0 0 1 8.688 2h6.624a2 2 0 0 1 1.414.586l4.688 4.688A2 2 0 0 1 22 8.688v6.624a2 2 0 0 1-.586 1.414l-4.688 4.688a2 2 0 0 1-1.414.586H8.688a2 2 0 0 1-1.414-.586z"/><path d="M8 12h8"/>',heart:'<path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/>',
 com:'<path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z"/>',send:'<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>',save:'<path d="m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z"/>',
 cal:'<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/><path d="M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01M16 18h.01"/>'};
const fmt=n=>n>=1e6?(n/1e6).toFixed(1).replace(/\.0$/,'')+'M':n>=1e4?Math.round(n/1e3)+'K':n>=1e3?(n/1e3).toFixed(1).replace(/\.0$/,'')+'K':''+n;
const cap1=t=>String(t).charAt(0).toUpperCase()+String(t).slice(1);   /* "3 hours ago": a capital first letter, no all-caps */
const RM=()=>matchMedia('(prefers-reduced-motion:reduce)').matches;
/* accounts: the deck's (0…), daily.heatmap (-1), saved.cards (-2) */
const HACC={h:'daily.heatmap',c:'#a6e3a1',ic:ICN.cal},SACC={h:'saved.cards',c:'#b4befe',ic:ICN.save};
const accOf=a=>a==-1?HACC:a==-2?SACC:F.acc[a];
const avatar=a=>`<span class="f-av" style="background:${a.c}">${SVG(a.ic)}</span>`;
function reviewHTML(n,o){cur={n,o};stage=1;FL=false;return `<div class="wrap"><div class="tinder-swipe tinder-swipe--answer">${face(true)}</div></div>`}   /* a story: as the reviewer draws it */
function answerHTML(n,o){cur={n,o};stage=1;FL=false;EXIN=true;const tg=SET.tags;SET.tags=false;const h=face(true);SET.tags=tg;EXIN=false;   /* no tags on a post: its caption has them as hashtags */
 return h.replace(/^(<div class="prettify-flashcard[^"]*">)<div class="prettify-deck">[\s\S]*?<\/div>(?:<hr[^>]*>|<div class="prettify-divider[^"]*"><\/div>)/,'$1')}   /* a post: no deck path on the card */
function settle(el){imgs(el);mj(el);el.querySelectorAll('.f-pb').forEach(clip);subs(el)}
function clip(b){if(b.classList.contains('open'))return;
 /* a carousel whose cards don't all fit drops to its first card (with 'more' if that needs it) */
 if(b.querySelector('.f-car')){const el=b.closest('.f-post'),p=el&&postOf(el);if([...b.querySelectorAll('.f-sl>.prettify-flashcard')].some(c=>c.scrollHeight>c.clientHeight+2)&&p){p.single=true;el.__s=0;el.querySelector('.f-dots')?.remove();b.innerHTML=bodyHTML(p);imgs(b);mj(b)}else return}
 const c=b.querySelector('.prettify-flashcard');b.classList.toggle('clip',!!c&&c.scrollHeight>c.clientHeight+2)}
/* ---- carousels: the post's cards side by side, swiped with snapping, dots under it; all as tall as the tallest, a shorter card's
   question and answer centred in it (the extra room split above and below) */
const CHEV='<div class="f-pmore"><button class="mcq-chev" aria-label="Show more"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg></button></div>';
const slides=p=>p.single||!p.more||!p.more.length?[{n:p.n,o:p.o}]:[{n:p.n,o:p.o}].concat(p.more);
const mid=h=>h.replace(/^(<div class="prettify-flashcard[^"]*">)([\s\S]*)(<\/div>)$/,'$1<div class="f-mid">$2</div>$3');
function bodyHTML(p){if(p.day)return dayHTML(p);const s=slides(p);if(s.length<2)return answerHTML(p.n,p.o)+CHEV;
 return `<div class="f-car">${s.map(c=>`<div class="f-sl">${mid(answerHTML(c.n,c.o))}</div>`).join('')}</div>`}
const cardAt=(el,p)=>slides(p)[Math.min(el.__s||0,slides(p).length-1)];   /* the card on show (hide and save act on it) */
document.addEventListener('scroll',e=>{const c=e.target;if(!c.classList||!c.classList.contains('f-car'))return;const el=c.closest('.f-post'),p=el&&postOf(el);if(!p)return;
 const i=Math.round(c.scrollLeft/Math.max(1,c.clientWidth));if(i===el.__s)return;el.__s=i;
 el.querySelectorAll('.f-dots i').forEach((d,k)=>d.classList.toggle('on',k===i));const sv=el.querySelector('.f-sv'),k=cardAt(el,p);if(sv&&k.n)sv.classList.toggle('on',F.saved.has(k.n[0]+':'+k.o))},true);
document.addEventListener('load',e=>{if(e.target.tagName!='IMG')return;ioBox(e.target);const b=e.target.closest('.f-pb');if(b)clip(b)},true);
const FD=$('#feed'),PFE=$('#pf');

/* ---- the line under an account name: the song (♪) and the card's directory take turns, sliding over each other every few seconds;
   a line too long for its space scrolls (a marquee) with its edges faded. tapping it mutes or unmutes */
const dir=n=>(DATA.paths[n[2]]||'').split(' › ').join(' · ');
function subHTML(items){items=items.filter(Boolean);if(!items.length)return'';
 return `<span class="f-sub${items.length>1?' two':''}">${items.map((t,i)=>`<span class="f-si2${i?'':' on'}"><span class="f-mq">${ESC(t)}</span></span>`).join('')}</span>`}
function subs(el){el.querySelectorAll('.f-si2').forEach(s=>{const m=s.firstElementChild,o=m.scrollWidth-s.clientWidth;s.classList.toggle('mq',o>2);if(o>2)m.style.setProperty('--mq',-(o+24)+'px')})}
setInterval(()=>{if(RM())return;document.querySelectorAll('.f-sub.two').forEach(s=>{const a=s.children,i=a[0].classList.contains('on')?1:0;a[i].classList.add('on');a[1-i].classList.remove('on')})},3800);

/* ---- posts (the feed's, a profile's): data-i is the post's place: a number (the feed), p<i> (the open profile), h (the heatmap post) */
const postOf=el=>{const i=el.dataset.i;return i=='h'?F.hp:i[0]=='p'?PF.list[+i.slice(1)]:F.posts[+i]};
const isSaved=p=>p.n&&F.saved.has(p.n[0]+':'+p.o);
const dotsHTML=p=>slides(p).length>1?`<div class="f-dots">${slides(p).map((_,k)=>`<i${k?'':' class="on"'}></i>`).join('')}</div>`:'';
const hashtags=c=>ESC(c).replace(/#[^\s#]+/g,'<span class="f-tag">$&</span>');
const actions=p=>`${dotsHTML(p)}<div class="f-pa"><button class="f-lk${p.liked?' on':''}" aria-label="Like">${SVG(ICN.heart)}<span class="f-lkn">${fmt(p.likes+(p.liked?1:0))}</span></button><button class="f-cm" aria-label="Comments">${SVG(ICN.com)}<span>${fmt(p.coms)}</span></button><button class="f-sh" aria-label="Share">${SVG(ICN.send)}</button><button class="f-sv${isSaved(p)?' on':''}" aria-label="Save">${SVG(ICN.save)}</button></div>`;
function postHTML(p,i){const a=accOf(p.a);return `<article class="f-post" data-i="${i}">
<header class="f-ph">${avatar(a)}<span class="f-pn"><b>${ESC(a.h)}</b>${subHTML([p.st&&'♪ '+p.st,p.n&&dir(p.n)])}</span>${p.n?`<button class="f-more" aria-label="More">${SVG(ICN.dots)}</button>`:''}</header>
<div class="f-pb f-load">${skel()}</div>
${actions(p)}
${p.cap?`<div class="f-pc"><b>${ESC(a.h)}</b> ${hashtags(p.cap)}</div>`:''}
${p.cm.length?`<div class="f-pv f-cm">View all ${fmt(p.coms)} comments</div>`:''}
${p.cm.map(([u,t])=>`<div class="f-pc f-cmt"><b>${ESC(u)}</b> ${ESC(t)}</div>`).join('')}
${p.t?`<div class="f-pt">${ESC(cap1(p.t))}</div>`:''}</article>`}

/* ---- the heatmap post: always first, from daily.heatmap. answers revealed per day (the app's medslave:activity), one block per month,
   Monday-first weeks, today at the right end; a tap on a day shows its count. caption: today and the streak */
const HM=['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'],dk=d=>d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
F.hp={a:-1,likes:0,coms:0,liked:false,cm:[]};let HJ='',ACT={};
const hmMax=m=>Math.max(0,...Object.values(m).map(Number).filter(isFinite));
/** one month's block: y, mo (0-11), its days up to last; the ringed day is `ring` */
function monthBlock(m,mx,y,mo,last,ring){let g='<i class="f"></i>'.repeat((new Date(y,mo,1).getDay()+6)%7);
 for(let d=1;d<=last;d++){const k=dk(new Date(y,mo,d)),c=+m[k]||0,l=c&&mx?Math.max(1,Math.ceil(4*c/mx)):0;g+=`<i class="l${l}${k==ring?' t':''}" data-k="${k}" data-c="${c}"></i>`}
 return `<div class="f-mb"><b>${HM[mo]}${mo?'':' '+y}</b><div class="f-hg">${g}</div></div>`}
function heatGrid(m){const t=new Date(),mx=hmMax(m);let o='';
 for(let i=11;i>=0;i--){const f=new Date(t.getFullYear(),t.getMonth()-i,1),y=f.getFullYear(),mo=f.getMonth();o+=monthBlock(m,mx,y,mo,i?new Date(y,mo+1,0).getDate():t.getDate(),dk(t))}return o}
function heatCap(m){const t=new Date();let n=+m[dk(t)]||0,s=0;const d=new Date(t);if(!n)d.setDate(d.getDate()-1);while(+m[dk(d)]>0){s++;d.setDate(d.getDate()-1)}
 return (n?`${n} card${n==1?'':'s'} today`:'no cards yet today')+(s>1?` · ${s}-day streak 🔥`:'')+' #consistency #dailygrind'}
function heatPost(m){const p=F.hp,a=HACC;if(!p.likes){p.likes=800+Math.random()*40000|0;p.coms=20+Math.random()*900|0}
 return `<article class="f-post" data-i="h"><header class="f-ph">${avatar(a)}<span class="f-pn"><b>${a.h}</b></span></header>
<div class="f-hmb"><div class="f-hm">${heatGrid(m)}</div><div class="f-hmt"></div></div>${actions(p)}
<div class="f-pc"><b>${a.h}</b> ${hashtags(heatCap(m))}</div><div class="f-pt">Today</div></article>`}
function heat(m){ACT=m||{};const j=JSON.stringify(ACT);if(j==HJ&&$('#posts [data-i="h"]'))return;HJ=j;const old=$('#posts [data-i="h"]'),t=document.createElement('div');t.innerHTML=heatPost(ACT);const el=t.firstElementChild;
 old?old.replaceWith(el):$('#posts').prepend(el);const h=el.querySelector('.f-hm');h.scrollLeft=h.scrollWidth}
document.addEventListener('click',e=>{const c=e.target.closest('.f-hg i[data-k]');if(!c)return;const d=new Date(c.dataset.k+'T00:00'),n=+c.dataset.c,t=c.closest('.f-post')?.querySelector('.f-hmt');
 if(t)t.textContent=d.toLocaleDateString(undefined,{weekday:'short',day:'numeric',month:'short',year:'numeric'})+' · '+n+' card'+(n==1?'':'s')+' done'});

/* ---- "online" loading: a post's card starts as a shimmering placeholder and turns up after a believable delay, mostly .2–.9s, now
   and then up to ~2s, once it scrolls near; more posts take a moment too. it's all local, it just shouldn't look it */
const LAG=()=>Math.random()<.15?900+Math.random()*1300:200+Math.random()*700;
function skel(){const w=[[86,64,72],[78,92,58,66],[70,88],[90,60,80,50]][Math.random()*4|0];
 return `<div class="f-sk" style="min-height:${120+Math.random()*110|0}px">${w.map(x=>`<i style="width:${x}%"></i>`).join('')}</div>`}
function arrive(b){if(b.dataset.t)return;b.dataset.t=1;const el0=b.closest('.f-post');setTimeout(()=>{const el=b.closest('.f-post');if(!el)return;const p=postOf(el);if(!p)return;
 b.classList.remove('f-load');b.innerHTML=bodyHTML(p);b.classList.add('f-in');settle(el)},el0&&el0.dataset.i=='p0'?0:LAG())}   /* the card a profile was opened from is there at once */
const near=root=>new IntersectionObserver(es=>es.forEach(e=>{if(e.isIntersecting){e.target.__o.unobserve(e.target);arrive(e.target)}}),{root,rootMargin:'300px 0px'});
const NEAR=near(FD);
function watch(e){if(!e.querySelector)return;const b=e.querySelector('.f-pb.f-load');if(b){if(e.closest('#pf'))arrive(b);else{b.__o=NEAR;NEAR.observe(b)}}if(e.classList.contains('f-post'))SEEN.observe(e)}   /* a profile's few posts load straight away (each after its delay) */
function addPosts(l){const box=$('#posts'),at=F.posts.length;F.posts.push(...l);box.querySelector('.f-loadmore')?.remove();
 const t=document.createElement('div');t.innerHTML=l.map((p,k)=>postHTML(p,at+k)).join('')+(l.length?'<div class="f-loadmore"><i class="lg"></i></div>':'');
 const els=[...t.children];els.forEach(e=>box.appendChild(e));els.forEach(watch);F.wait=false;subs(box)}

/* ---- the buttons */
function kick(b){b.classList.remove('go');void b.offsetWidth;b.classList.add('go')}   /* restarts a button's one-shot animation */
function like(el,p,v){p.liked=v;const b=el.querySelector('.f-lk');b.classList.toggle('on',v);b.classList.remove('pop','unpop');void b.offsetWidth;b.classList.add(v?'pop':'unpop');el.querySelector('.f-lkn').textContent=fmt(p.likes+(v?1:0))}
let LT=0,LX=null;
/* a post's menu (the three dots): Hide keeps the card out of posts, stories and reels (Settings › Hidden cards brings it back) */
function menuClose(){document.querySelectorAll('.f-menu').forEach(m=>m.remove())}
const plain=n=>{const d=document.createElement('div');d.innerHTML=String(n[4][0]||'').replace(/<ol class="mcq"[\s\S]*?<\/ol>/,'').replace(/\{\{c\d+::([\s\S]*?)(::[\s\S]*?)?\}\}/g,'$1').replace(/<br\s*\/?>/gi,' ');return d.textContent.replace(/\s+/g,' ').trim().slice(0,160)};
function hidePost(p){const k=p.n[0]+':'+p.o;A_.hide(String(p.n[0]),p.o,plain(p.n),dir(p.n));
 document.querySelectorAll('.f-post').forEach(x=>{const q=postOf(x);if(!q||!q.n||!slides(q).some(c=>c.n[0]+':'+c.o==k))return;   /* any post with that card, a carousel's too */
  if(RM()){x.remove();return}x.style.height=x.offsetHeight+'px';x.style.overflow='hidden';void x.offsetHeight;x.classList.add('f-gone');x.style.height='0px';setTimeout(()=>x.remove(),380)})}
document.addEventListener('scroll',menuClose,true);
function postClick(e){if(!e.target.closest('.f-menu,.f-more'))menuClose();const el=e.target.closest('.f-post');if(!el)return;const p=postOf(el);if(!p)return;const a=accOf(p.a);
 const mo=e.target.closest('.f-more');if(mo){const open=el.querySelector('.f-menu');menuClose();if(!open){el.insertAdjacentHTML('beforeend',`<div class="f-menu"><button class="f-hide">${SVG(ICN.stop)}<span>Hide</span></button></div>`)}return}
 if(e.target.closest('.f-hide')){menuClose();return hidePost(cardAt(el,p))}
 if(e.target.closest('.f-ph .f-av,.f-ph .f-pn b'))return openAccount(p);
 if(e.target.closest('.f-sub'))return A_.mute();
 if(e.target.closest('.f-lk'))return like(el,p,!p.liked);
 const cm=e.target.closest('.f-pa .f-cm'),sh=e.target.closest('.f-sh'),sv=e.target.closest('.f-sv');
 if(cm){kick(cm);return setTimeout(()=>A_.com(a.h,p.coms),RM()?0:160)}
 if(e.target.closest('.f-pv.f-cm'))return A_.com(a.h,p.coms);
 if(sh){kick(sh);return setTimeout(()=>A_.share(),RM()?0:260)}
 if(sv){kick(sv);if(!p.n)return sv.classList.toggle('on');const cc=cardAt(el,p),k=cc.n[0]+':'+cc.o,on=!F.saved.has(k);on?F.saved.add(k):F.saved.delete(k);A_.save(String(cc.n[0]),cc.o,on);
  document.querySelectorAll('.f-post').forEach(x=>{const q=postOf(x);if(q&&q.n){const c2=cardAt(x,q);if(c2.n[0]+':'+c2.o==k)x.querySelector('.f-sv').classList.toggle('on',on)}});return}
 const b=e.target.closest('.f-pb');if(!b)return;
 if(e.target.closest('.f-pmore')){b.classList.toggle('open');return}   /* the chevron opens a long post and folds it again */
 const now=Date.now();if(now-LT<320&&LX===el){LT=0;const h=document.createElement('i');h.className='f-burst';h.innerHTML=SVG(ICN.heart);b.appendChild(h);setTimeout(()=>h.remove(),900);if(!p.liked)like(el,p,true)}else{LT=now;LX=el}}
$('#posts').addEventListener('click',postClick);$('#pfl').addEventListener('click',postClick);
FD.addEventListener('scroll',()=>{if(!F.wait&&F.posts.length&&FD.clientHeight+FD.scrollTop>FD.scrollHeight-FD.clientHeight*1.5){F.wait=true;F.asked=Date.now();A_.more()}},{passive:true});
$('#eimp').onclick=()=>A_.imp();$('#wm').onclick=()=>A_.credits();$('#gear').onclick=()=>A_.settings();$('#arch').onclick=()=>{PF.first=null;A_.saved()};

/* ---- pull to refresh: dragging down from the top pulls the stories and posts down with a circular arrow that turns as it comes;
   let go past PULL and it spins while a new set of posts loads (the stories and the heatmap post stay) */
const PULL=70,PTR={y:null,d:0,busy:false};
function ptrSet(d,anim){const k=[$('#stories'),$('#posts'),$('#empty')],p=$('#ptr');PTR.d=d;
 k.forEach(e=>{e.style.transition=anim?'transform .3s cubic-bezier(.2,.8,.2,1)':'none';e.style.transform=d?`translateY(${d}px)`:''});
 p.style.transition=anim?'opacity .3s,transform .3s cubic-bezier(.2,.8,.2,1)':'none';p.style.opacity=Math.min(1,d/PULL);p.style.transform=`translate(-50%,${Math.min(d,PULL)*.55}px) rotate(${d*3}deg)`}
FD.addEventListener('touchstart',e=>{PTR.y=!PTR.busy&&FD.scrollTop<=0&&e.touches.length==1?e.touches[0].clientY:null},{passive:true});
FD.addEventListener('touchmove',e=>{if(PTR.y==null)return;const dy=e.touches[0].clientY-PTR.y;if(dy<=0){if(PTR.d)ptrSet(0);return}
 if(e.cancelable)e.preventDefault();ptrSet(Math.min(130,dy*.5))},{passive:false});
FD.addEventListener('touchend',()=>{if(PTR.y==null)return;PTR.y=null;if(PTR.d<PULL)return ptrSet(0,true);
 PTR.busy=true;PTR.t=Date.now();ptrSet(PULL,true);$('#ptr').classList.add('spin');A_.refresh()});

/* ---- songs: about 80% of posts and stories have one. the song plays for the post most in view (or the story on screen) */
const VIS=new Map(),SEEN=new IntersectionObserver(es=>{es.forEach(e=>VIS.set(e.target,e.intersectionRatio));tune()},{threshold:[0,.25,.5,.75,1]});
let SONG=null;
function tune(){let s='';
 if(SV.on){const it=SV.list[SV.g]?.items[SV.i];s=it&&it.s||''}
 else{let best=null,r=.45;VIS.forEach((v,el)=>{if(!el.isConnected){VIS.delete(el);return}if(!!el.closest('#pf')!=PF.on)return;if(v>r){r=v;best=el}});
  const p=best&&postOf(best);s=p&&p.s||''}
 if(s!==SONG){SONG=s;A_.play(s)}}

/* ---- stories: the row of accounts (unwatched first), and the viewer (one page per account, swiped sideways; tap right / left for the
   next / previous story, hold to pause; each runs DUR ms). profiles' highlights play in the same viewer */
const SV={g:0,i:0,el:0,last:0,hold:false,raf:0,on:false,DUR:8000,list:[],src:''};
function rings(){if(!SV.on)F.groups.sort((a,b)=>a.seen-b.seen);   /* unwatched first (a stable sort keeps the rest in order) */
 $('#stories').innerHTML=F.groups.map((g,i)=>{const a=F.acc[g.a];return `<button class="f-st" data-g="${i}"><span class="f-ring${g.seen?' seen':''}">${avatar(a)}</span><span class="f-stn">${ESC(a.h)}</span></button>`}).join('');
 $('#stories').hidden=!F.groups.length}
$('#stories').addEventListener('click',e=>{const b=e.target.closest('.f-st');if(b)svOpen(+b.dataset.g,F.groups,'#stories')});
$('#pfs').addEventListener('click',e=>{const b=e.target.closest('.f-hl .f-st');if(b)svOpen(+b.dataset.g,PF.hl,'#pfs .f-hl')});
/* the circle a story grows from / shrinks into */
function ringAt(g){const r=$(`${SV.src} .f-st[data-g="${g}"] .f-ring`);if(!r)return null;const b=r.getBoundingClientRect();return b.bottom>0&&b.top<innerHeight?b:null}
const page=g=>$('#svp').children[g];
function svBuild(){$('#svp').innerHTML=SV.list.map(g=>{const a=accOf(g.a);return `<section class="f-sg"><div class="f-bars">${g.items.map(()=>'<span class="f-bar"><i></i></span>').join('')}</div>
<div class="f-sh"><span class="f-sa">${avatar(a)}<span class="f-pn"><span class="f-pnl"><b>${ESC(g.hl?g.t:a.h)}</b>${g.hl?'':`<small>${ESC(g.t)}</small>`}</span><span class="f-sst"></span></span></span><button class="f-sx" aria-label="Close">×</button></div><div class="f-sc"></div></section>`}).join('')}
function svShow(g,i){const G=SV.list[g];if(!G)return svClose();SV.g=g;SV.i=i;SV.el=0;
 [...$('#svp').children].forEach((s,k)=>{if(k!=g)s.querySelector('.f-sc').innerHTML=''});
 const s=page(g);s.querySelectorAll('.f-bar i').forEach((b,k)=>b.style.width=k<i?'100%':'0');
 const it=G.items[i];s.querySelector('.f-sst').innerHTML=subHTML([it.st&&'♪ '+it.st]);subs(s);
 const c=s.querySelector('.f-sc');c.innerHTML='<div class="f-si">'+reviewHTML(it.n,it.o)+'</div>';c.scrollTop=0;imgs(c);mj(c);
 if(!G.seen){G.seen=true;if(!G.hl)A_.seen(G.a)}tune()}
function svTick(t){if(!SV.on)return;const dt=SV.last?t-SV.last:0;SV.last=t;if(!SV.hold)SV.el+=dt;
 const b=page(SV.g)?.querySelectorAll('.f-bar i')[SV.i];if(b)b.style.width=Math.min(100,SV.el/SV.DUR*100)+'%';
 if(SV.el>=SV.DUR)svNext();SV.raf=requestAnimationFrame(svTick)}
function svGo(g){const p=$('#svp');p.scrollTo({left:g*p.clientWidth,behavior:'smooth'});svShow(g,0)}
function svNext(){const G=SV.list[SV.g];if(SV.i<G.items.length-1)svShow(SV.g,SV.i+1);else if(SV.g<SV.list.length-1)svGo(SV.g+1);else svClose()}
function svPrev(){if(SV.i>0)svShow(SV.g,SV.i-1);else if(SV.g>0)svGo(SV.g-1);else svShow(SV.g,0)}
function svOpen(g,list,src){SV.list=list;SV.src=src;svBuild();const sv=$('#sv'),r=ringAt(g);sv.classList.add('on');SV.on=true;SV.last=0;A_.story(true);
 if(r&&!RM()){sv.style.transformOrigin=`${r.left+r.width/2}px ${r.top+r.height/2}px`;sv.animate([{transform:`scale(${r.width/innerWidth})`,opacity:.3,borderRadius:'50%'},{transform:'none',opacity:1,borderRadius:'0'}],{duration:320,easing:'cubic-bezier(.2,.8,.2,1)'})}
 const p=$('#svp');p.scrollLeft=g*p.clientWidth;svShow(g,0);cancelAnimationFrame(SV.raf);SV.raf=requestAnimationFrame(svTick)}
function svClose(now){if(!SV.on)return;SV.on=false;cancelAnimationFrame(SV.raf);const sv=$('#sv'),r=!now&&!RM()&&ringAt(SV.g),done=()=>{if(F.next){F.groups=F.next;F.next=null}rings();tune()};
 A_.story(false);
 if(r){sv.style.transformOrigin=`${r.left+r.width/2}px ${r.top+r.height/2}px`;sv.animate([{transform:'none',opacity:1,borderRadius:'0'},{transform:`scale(${r.width/innerWidth})`,opacity:0,borderRadius:'50%'}],{duration:240,easing:'cubic-bezier(.4,0,.6,1)'}).onfinish=()=>{if(!SV.on){sv.classList.remove('on');$('#svp').innerHTML=''}};return done()}
 sv.classList.remove('on');$('#svp').innerHTML='';done()}
let SVS=0;$('#svp').addEventListener('scroll',()=>{clearTimeout(SVS);SVS=setTimeout(()=>{const p=$('#svp'),g=Math.round(p.scrollLeft/p.clientWidth);if(SV.on&&g!=SV.g)svShow(g,0)},120)},{passive:true});
/* a tap: right 70% = next, left 30% = previous; holding pauses; the account at the top opens its profile */
let PD=null,HT=0;
$('#svp').addEventListener('pointerdown',e=>{if(e.target.closest('.f-sx,.f-sa'))return;PD={x:e.clientX,y:e.clientY,t:Date.now()};HT=setTimeout(()=>{SV.hold=true},180)});
$('#svp').addEventListener('pointerup',e=>{clearTimeout(HT);const d=PD;PD=null;const held=SV.hold;SV.hold=false;
 if(e.target.closest('.f-sx'))return svClose();
 if(e.target.closest('.f-sa')){const G=SV.list[SV.g],it=G.items[SV.i];if(G.a<0)return;svClose(true);
  return openAccount({a:G.a,n:it.n,o:it.o,s:it.s,st:it.st,likes:120+Math.random()*60000|0,coms:4+Math.random()*900|0,cap:'',cm:[],t:G.t&&!G.hl?G.t+' ago':'just now'})}
 if(!d||held||Math.hypot(e.clientX-d.x,e.clientY-d.y)>10)return;
 e.clientX<innerWidth*.3?svPrev():svNext()});
$('#svp').addEventListener('pointercancel',()=>{clearTimeout(HT);PD=null;SV.hold=false});

/* ---- profiles: a page that slides in over the feed. an account's (made by the app: name, id, counts, bio, highlights, its posts with
   the card it was opened from first), daily.heatmap's (a post for every day cards were done) and saved.cards (the archive) */
const PF={on:false,list:[],hl:[],first:null,a:0,more:false,wait:false};
function openAccount(p){if(p.a==-1)return heatProfile();if(p.a<0)return;PF.first=p;A_.profile(p.a)}
function pfRender(j){if(!PF.on){PF.on=true;PFE.classList.add('on');PFE.scrollTop=0;A_.pfOpen(true);void PFE.offsetWidth;PFE.classList.add('in')}else PFE.scrollTop=0;
 PF.a=j.a;PF.list=(PF.first?[PF.first]:[]).concat(j.list);PF.first=null;PF.more=j.a>=0;
 PF.hl=(j.hl||[]).map(h=>({a:j.a,t:h.t,hl:true,ic:h.ic,items:h.items,seen:true}));
 const a=accOf(j.a),st=(n,l)=>n<0?'':`<div><b>${n.toLocaleString()}</b><small>${l}</small></div>`;
 $('#pfs').innerHTML=`<header class="f-top f-pft"><button class="f-back" aria-label="Back">‹</button><b>${ESC(a.h)}</b></header>
<section class="f-prof"><div class="f-pr1"><span class="f-av f-big" style="background:${a.c}">${SVG(a.ic)}</span><div class="f-stats">${st(j.posts,'posts')}${st(j.followers,'followers')}${st(j.following,'following')}</div></div>
<div class="f-bio"><b>${ESC(j.name)}</b>${j.bio?`<p>${ESC(j.bio)}</p>`:''}</div>
${j.a>=0?'<button class="nx f-follow">Follow</button>':''}
${PF.hl.length?`<div class="f-hl">${PF.hl.map((h,i)=>`<button class="f-st" data-g="${i}"><span class="f-ring seen"><span class="f-av f-hlc">${SVG(h.ic)}</span></span><span class="f-stn">${ESC(h.t)}</span></button>`).join('')}</div>`:''}</section>`;
 const l=$('#pfl');l.innerHTML='';pfAdd(PF.list,0);if(!PF.list.length)l.innerHTML=`<p class="f-none">${j.a==-2?'Saved cards show up here. Tap the bookmark on a post to save it.':'No posts yet.'}</p>`;
 setTimeout(tune,350)}
function pfAdd(l,at){const box=$('#pfl');box.querySelector('.f-loadmore')?.remove();const t=document.createElement('div');
 t.innerHTML=l.map((p,k)=>postHTML(p,'p'+(at+k))).join('')+(PF.more&&l.length?'<div class="f-loadmore"><i class="lg"></i></div>':'');[...t.children].forEach(e=>{box.appendChild(e);watch(e)});subs(box);PF.wait=false}
function pfClose(now){if(!PF.on)return;PF.on=false;A_.pfOpen(false);PFE.classList.remove('in');const done=()=>{if(PF.on)return;PFE.classList.remove('on');$('#pfl').innerHTML='';$('#pfs').innerHTML='';tune()};
 if(now||RM())return done();setTimeout(done,260)}
$('#pfs').addEventListener('click',e=>{if(e.target.closest('.f-back'))return pfClose();const f=e.target.closest('.f-follow');if(f){f.classList.toggle('on');f.textContent=f.classList.contains('on')?'Following':'Follow'}});
PFE.addEventListener('scroll',()=>{if(PF.more&&!PF.wait&&PFE.clientHeight+PFE.scrollTop>PFE.scrollHeight-PFE.clientHeight*1.5){PF.wait=true;A_.pmore()}},{passive:true});
/* daily.heatmap's profile: a post for each day cards were done, newest first: that day's count and its month with the day ringed */
function heatProfile(){const days=Object.keys(ACT).filter(k=>+ACT[k]>0).sort().reverse(),tot=days.reduce((s,k)=>s+ +ACT[k],0);
 pfRender({a:-1,name:'Daily Heatmap',bio:`${tot.toLocaleString()} cards on ${days.length} day${days.length==1?'':'s'}`,posts:days.length,followers:-1,following:-1,hl:[],
  list:days.map(k=>({a:-1,day:k,c:+ACT[k],likes:40+Math.random()*4000|0,coms:Math.random()*200|0,cap:`${ACT[k]} card${ACT[k]==1?'':'s'} done #dailygrind`,cm:[],t:new Date(k+'T00:00').toLocaleDateString(undefined,{weekday:'long',day:'numeric',month:'long'})}))})}
function dayHTML(p){const d=new Date(p.day+'T00:00'),y=d.getFullYear(),mo=d.getMonth();
 return `<div class="f-day"><b>${p.c.toLocaleString()}</b><small>card${p.c==1?'':'s'} done</small><div class="f-hm f-dayhm">${monthBlock(ACT,hmMax(ACT),y,mo,new Date(y,mo+1,0).getDate(),p.day)}</div></div>`}

/* ---- the app's calls */
const R={
 theme(t,dark){document.documentElement.dataset.theme=t;document.body.classList.toggle('night_mode',dark);document.body.classList.toggle('nightMode',dark);
  document.body.style.setProperty('--bgc',getComputedStyle(document.body).backgroundColor)},
 feed(d){DATA.paths=d.paths;SET.source=!!d.source;SET.expl=d.expl!==false;SET.tags=!!d.tags;F.acc=d.acc;F.posts=[];F.groups=d.groups;F.saved=new Set(d.saved||[]);$('#posts').innerHTML='';svClose(true);pfClose(true);rings();
  $('#empty').hidden=!!d.acc.length;HJ='';heat(d.act);addPosts(d.posts);FD.scrollTop=0},
 heat(m){heat(m)},
 refreshed(l){setTimeout(()=>{$('#posts').querySelectorAll('.f-post:not([data-i="h"]),.f-loadmore').forEach(e=>e.remove());F.posts=[];F.wait=false;addPosts(l);
  $('#ptr').classList.remove('spin');ptrSet(0,true);PTR.busy=false},Math.max(0,600+Math.random()*900-(Date.now()-PTR.t)))},   /* a believable wait, then the new posts */
 insets(t,b){const s=document.documentElement.style;s.setProperty('--st',t+'px');s.setProperty('--sb',b+'px')},   /* status / navigation bar heights: the page runs behind the status bar */
 posts(l){setTimeout(()=>addPosts(l),Math.max(0,500+Math.random()*900-(Date.now()-(F.asked||0))))},
 stories(g){if(SV.on){F.next=g;return}F.groups=g;rings()},   /* new stories wait while the viewer is open */
 profile(j){pfRender(j)},
 openAccount(ai,p){svClose(true);PF.first=p;A_.profile(ai)},   /* from Reels: that card's account, the card first */
 pposts(l){if(!l.length)PF.more=false;const at=PF.list.length;PF.list.push(...l);setTimeout(()=>pfAdd(l,at),400+Math.random()*700)},
 closeProfile(){pfClose()},
 savedSet(l){F.saved=new Set(l);document.querySelectorAll('.f-post').forEach(x=>{const q=postOf(x),b=x.querySelector('.f-sv');if(q&&q.n&&b)b.classList.toggle('on',F.saved.has(q.n[0]+':'+q.o))})},   /* saved from Reels */
 opts(source,expl,tags){SET.source=!!source;SET.expl=expl!==false;SET.tags=!!tags},   /* Settings › Reviewing (cards drawn from now on) */
 nocom(v){document.body.classList.toggle('nocom',!!v)},   /* Settings › Appearance › Hide comments */
 close(){svClose()},
 replay(){SONG=null;tune()},   /* the feed is back on screen: its song again */
 /* the songs that are on changed (a genre or playlist switched, a playlist edited): a post's or story's song that's no longer on is
    swapped for one that is (the same one each time for the same song), and the line under the account name follows */
 songsOn(keys){const on=new Set(keys),swap=s=>{if(!s||on.has(s)||!keys.length)return s;let h=0;for(const c of String(s))h=h*31+c.charCodeAt(0)|0;return keys[Math.abs(h)%keys.length]};
  const fix=x=>{if(x&&x.s&&!on.has(x.s)){x.s=x.st=swap(x.s)}};
  F.posts.forEach(fix);PF.list.forEach(fix);F.groups.forEach(g=>g.items.forEach(fix));PF.hl.forEach(g=>g.items.forEach(fix));if(F.next)F.next.forEach(g=>g.items.forEach(fix));
  document.querySelectorAll('.f-post').forEach(el=>{const p=postOf(el),n=el.querySelector('.f-ph .f-pn');if(!p||!p.n||!n)return;n.querySelector('.f-sub')?.remove();n.insertAdjacentHTML('beforeend',subHTML([p.st&&'♪ '+p.st,dir(p.n)]))});
  subs(document);if(SV.on)svShow(SV.g,SV.i);else{SONG=null;tune()}},
 top(i){if(SV.on&&i)svClose(true);if(PF.on)pfClose(!!i);FD.scrollTo({top:0,behavior:i?'instant':'smooth'})}};
