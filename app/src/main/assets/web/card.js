/* medslave · card.js: the card face inside the app's WebView. A copy of the site's js/card.js (face, cloze, shapes, IO one by one,
   trimF, MathJax, sequential clozes, flip/fade), driven by the app through R.* and reporting back through the App bridge:
   App.st(left, n): sequential clozes still hidden + the step size. App.tap(): a pulsing cloze was tapped (= Next).
   App.h(px): the card's full height in CSS px (Reels Mode keeps tall cards above the account row). App.sc(top, bottom): whether the
   card's own scroll is at its top / bottom (a card taller than the view scrolls inside itself). App.ready(seq): a draw is on the page */
const A_=window.App||{st(){},tap(){},h(){},sc(){},ready(){},pick(){},ex(){},mv(){}};
/* an MCQ's option tapped on the question side: that's the answer. the answer side is redrawn with it marked, and the app is told
   whether it was right (App.pick), which it uses as the verdict when the card moves on */
document.addEventListener('click',e=>{const l=e.target.closest('#stage .option-group label[data-o]');if(!l||stage||!cur)return;e.preventDefault();
 MQ.picked=+l.dataset.o;const m=mcqOf(cur.n[4].map(trimF));if(!m)return;
 {const w=document.querySelector('#stage .flip-wrap');if(w){w.classList.replace('flip-wrap','fade-wrap');w.querySelector(':scope>.flip-front').className='fade-front';w.querySelector(':scope>.flip-back').className='fade-back'}}   /* a picked option fades to the answer rather than flipping */
 const b=document.querySelector('#stage .flip-back,#stage .fade-back');if(b){b.innerHTML=face(1);imgs(b);mj(b);fitX();watchSz()}   /* the redrawn answer side's images and maths */
 l.classList.add(MQ.picked==m.right?'highlight-correct':'highlight-wrong');A_.pick(MQ.picked==m.right)},true);
function render(){if(!FL){const A=stage==1;return `<div class="tinder-swipe${A?' tinder-swipe--answer':''}" id="swipe">${face(A)}</div>`}
const m=cur.n[1]=='b'?'flip':'fade';   /* basic cards flip; cloze + image-occlusion fade in place */
return `<div class="tinder-swipe" id="swipe"><div class="${m}-wrap${stage?' flipped half':''}"><div class="${m}-front">${face(0)}</div><div class="${m}-back">${face(1)}</div></div></div>`}
function draw(){const st=$('#stage');st.innerHTML='<div class="wrap">'+render()+'</div>';
 imgs(st);
 mj(st);st.querySelectorAll('.prettify-deck').forEach(d=>d.scrollLeft=d.scrollWidth);fitX();watchSz();
 {const b=$('#allClozeBtn');if(b)b.onclick=()=>revealAll(b)}syncNxt()}
/* the card on screen: in Flip, the face showing */
TURN=true;
/* the card on show; an MCQ answer turned over (Hide explanation) gives the side that's facing */
const card=()=>{const w=document.querySelector(FL&&stage?'#stage .flip-back,#stage .fade-back':'#stage .flip-front,#stage .fade-front,#swipe');if(!w)return null;
 const r=w.querySelector(':scope>.mcq-flip>.mcq-rot');if(r)return r.querySelector(':scope>'+(r.classList.contains('turned')?'.mcq-back':'.mcq-front')+'>.prettify-flashcard');
 return w.querySelector(':scope>.prettify-flashcard')};
function scRep(){const c=card();A_.sc(!c||c.scrollTop<=1,!c||c.scrollTop+c.clientHeight>=c.scrollHeight-2)}
/* tables and the deck path that fit get .nsx; the card's full height (as if it didn't scroll) and its scroll state go to the app */
function fitX(){document.querySelectorAll('#stage table,#stage .prettify-deck,#stage [style*="overflow"]').forEach(e=>e.classList.toggle('nsx',e.scrollWidth<=e.clientWidth+1));document.querySelectorAll('#stage .prettify-deck').forEach(deckFade);
 const c=card(),x=c&&c.parentNode.querySelector(':scope>.mcq-exc');A_.h((c?c.scrollHeight+c.offsetHeight-c.clientHeight:0)+(x?x.offsetHeight+10:0));scRep()}   /* the card's full height, and an MCQ's explanation card under it */
const RSZ=new ResizeObserver(()=>fitX());
/* re-measure whenever the card or anything in it changes size: a card at its height cap stops growing, but images and MathJax can still
   make its content taller, and the app must know it can scroll again (a swipe only moves on from a card that can't) */
function watchSz(){const st=$('#stage');RSZ.disconnect();if(st.firstElementChild)RSZ.observe(st.firstElementChild);st.querySelectorAll('.prettify-flashcard>*,.prettify-flashcard .f-mid>*').forEach(e=>RSZ.observe(e))}
function deckFade(d){d.classList.toggle('sl',d.scrollLeft>1);d.classList.toggle('sr',d.scrollLeft+d.clientWidth<d.scrollWidth-1)}
$('#stage').addEventListener('scroll',e=>{const d=e.target;if(!d.classList)return;if(d.classList.contains('prettify-deck'))deckFade(d);else if(d.classList.contains('prettify-flashcard')){scRep();A_.mv()}},true);   /* mv: it scrolled during this touch */
addEventListener('resize',fitX);$('#stage').addEventListener('load',e=>{if(e.target.tagName=='IMG')ioBox(e.target);fitX()},true);
$('#stage').addEventListener('dragstart',e=>e.preventDefault());
/* a touch on an MCQ's explanation card: tell the app which ways it can still scroll, so a swipe there scrolls it rather than the reel */
document.addEventListener('touchstart',e=>{const x=e.target.closest&&e.target.closest('#stage .mcq-exc');if(x)A_.ex(x.scrollTop>1,x.scrollTop+x.clientHeight<x.scrollHeight-2)},{passive:true});
const front=()=>document.querySelector('#stage .flip-front,#stage .fade-front')||$('#stage');
function reveal(n){const q='.cloze:not(.cloze-inactive):not(.cloze-revealed)',r=front();[...r.querySelectorAll(q)].slice(0,n).forEach(c=>{if(c.hasAttribute('data-cloze'))c.innerHTML=c.getAttribute('data-cloze');c.classList.add('cloze-revealed')});mj(r);syncNxt();fitX()}
function seqLeft(){if(stage||!document.querySelector('#stage .sequentialCloze'))return 0;return front().querySelectorAll('.cloze:not(.cloze-inactive):not(.cloze-revealed)').length}
function seqN(){const s=document.querySelector('#stage .sequentialCloze');return Math.max(1,+(s&&s.dataset.n)||1)}
/* the app's Next button shows 'Next Cloze' while more is hidden than one step reveals; the next group pulses */
function syncNxt(){const n=seqLeft();document.querySelectorAll('#stage .cloze-pulse').forEach(e=>e.classList.remove('cloze-pulse'));
 if(n)[...front().querySelectorAll('.cloze:not(.cloze-inactive):not(.cloze-revealed)')].slice(0,seqN()).forEach(f=>f.classList.add('cloze-pulse'));A_.st(n,seqN())}
$('#stage').addEventListener('click',e=>{if(e.target.closest&&e.target.closest('.cloze-pulse'))A_.tap()});
function revealAll(b){const r=document.querySelector('#stage .flip-back,#stage .fade-back')||$('#stage');r.querySelectorAll('.cloze-inactive[data-cloze]').forEach(c=>{c.innerHTML=c.getAttribute('data-cloze');c.classList.add('cloze-revealed')});mj(r);b.style.display='none';fitX()}
/* the answer in Flip: basic cards flip, cloze + occlusion fade; the fade glides the card's height from the question's to the answer's */
function answer(rm){stage=1;const w=document.querySelector('#stage .flip-wrap,#stage .fade-wrap');if(!w)return;document.querySelectorAll('#stage .prettify-flashcard').forEach(c=>c.scrollTop=0);fitX();
 if(w.classList.contains('fade-wrap')&&!rm){const h0=w.querySelector('.fade-front').offsetHeight,b=w.querySelector('.fade-back>.prettify-flashcard'),h1=b?b.offsetHeight:h0;w.classList.add('flipped');
  const an=w.animate([{height:h0+'px'},{height:h1+'px'}],{duration:350,easing:'ease',fill:'forwards'});an.onfinish=()=>{w.classList.add('half');an.cancel();fitX()}}
 else{w.classList.add('flipped');setTimeout(()=>{w.classList.add('half');fitX()},rm?0:76)}syncNxt()}
/* underglow on the answer side: v from -1 (missed it, red) to 1 (knew it, green) */
function glow(v){const k=$('#swipe');if(!k)return;k.classList.toggle('gl-ok',v>0);k.classList.toggle('gl-no',v<0);k.style.setProperty('--glow-opacity',(Math.min(1,Math.abs(v))*.55).toFixed(3))}
/* the app's calls */
const R={
 init(paths,source,expl,tags){DATA.paths=paths;R.opts(source,expl,tags)},
 opts(source,expl,tags){SET.source=!!source;SET.expl=expl!==false;SET.tags=!!tags},
 hidex(v){SET.hidex=!!v},
 /* turn an MCQ answer over to its explanation and back: ax 'y' sideways (Flip, Reels), 'x' up/down (Tinder); dir the swipe's way */
 turn(ax,dir){const r=document.querySelector('#stage .mcq-rot');if(!r)return;r.dataset.ax=ax;const a=(+r.dataset.a||0)+dir*180;r.dataset.a=a;
  r.style.transform=ax=='y'?`rotateY(${a}deg)`:`rotateX(${-a}deg)`;r.classList.toggle('turned',Math.abs(a/180)%2==1);setTimeout(scRep,480)},   /* Settings › Reviewing */
 theme(t,dark){document.documentElement.dataset.theme=t;document.body.classList.toggle('night_mode',dark);document.body.classList.toggle('nightMode',dark)},
 show(seq,n,o,s,flip){cur={n,o};stage=s;FL=!!flip;draw();requestAnimationFrame(()=>A_.ready(seq))},
 clear(){cur=null;$('#stage').innerHTML='';A_.h(0)},
 answer(rm){answer(rm)},
 step(){reveal(seqN())},
 glow(v){glow(v)},
 long(v){$('#stage').classList.toggle('long',!!v)},
 room(px){document.documentElement.style.setProperty('--room',px+'px')}};
