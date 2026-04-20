'use strict';

// ── Setup ─────────────────────────────────────────────────────
const canvas = document.getElementById('gameCanvas');
const ctx    = canvas.getContext('2d');
const W = canvas.width, H = canvas.height;

// ── DOM ───────────────────────────────────────────────────────
const overlay  = document.getElementById('game-overlay');
const oTitle   = document.getElementById('overlay-title');
const oSub     = document.getElementById('overlay-sub');
const oIcon    = document.getElementById('overlay-icon');
const startBtn = document.getElementById('start-btn');
const scoreEl  = document.getElementById('score-display');
const levelEl  = document.getElementById('level-display');
const livesEl  = document.getElementById('lives-display');
const hiEl     = document.getElementById('hi-score-display');

// ── Brick Grid ────────────────────────────────────────────────
const COLS = 10, PAD = 6, LEFT = 20, TOP = 55;
const BW   = Math.floor((W - LEFT * 2 - PAD * (COLS - 1)) / COLS);
const BH   = 22;

const COLORS = ['#ff6b6b','#ffd93d','#6bcb77','#4d96ff','#c77dff','#aaaaaa'];
const PTS    = [10, 20, 5, 15, 25, 8];

// ── State ─────────────────────────────────────────────────────
let score, level, lives, hiScore, bricks, ball, wide, playing;
hiScore = +localStorage.getItem('bx_hi') || 0;

// ── Paddle ────────────────────────────────────────────────────
const pad = { x: W/2 - 55, y: H - 36, w: 110, h: 14 };
const keys = {};
let mouseX = null;

document.addEventListener('keydown', e => {
  keys[e.key] = true;
  if (e.key === ' ') launch();
  if ((e.key === 'r' || e.key === 'R') && !playing) startGame();
});
document.addEventListener('keyup',  e => delete keys[e.key]);
canvas.addEventListener('mousemove', e => {
  const r = canvas.getBoundingClientRect();
  mouseX = (e.clientX - r.left) * (W / r.width);
});
canvas.addEventListener('click', launch);

// ── Init ──────────────────────────────────────────────────────
function startGame() {
  score = 0; level = 1; lives = 3; wide = false; playing = true;
  buildBricks();
  resetBall();
  overlay.classList.add('hidden');
  hud();
  loop();
}

function buildBricks() {
  bricks = [];
  const rows = Math.min(5 + level, 10);
  for (let r = 0; r < rows; r++)
    for (let c = 0; c < COLS; c++) {
      const i = Math.min(r + Math.floor(level / 2), COLORS.length - 1);
      bricks.push({ x: LEFT + c*(BW+PAD), y: TOP + r*(BH+PAD), color: COLORS[i], pts: PTS[i], hits: i === 5 ? 2 : 1, alive: true });
    }
}

function resetBall() {
  const pw = wide ? pad.w * 1.7 : pad.w;
  ball = { x: pad.x + pw/2, y: pad.y - 9, vx: 3, vy: -5, r: 8, stuck: true };
}

function launch() {
  if (ball?.stuck) ball.stuck = false;
}

// ── Game Loop ─────────────────────────────────────────────────
let raf;
function loop() {
  if (!playing) return;
  update();
  draw();
  raf = requestAnimationFrame(loop);
}

function update() {
  // Move paddle
  const pw = wide ? pad.w * 1.7 : pad.w;
  if (keys['ArrowLeft'])  pad.x -= 10;
  if (keys['ArrowRight']) pad.x += 10;
  if (mouseX !== null)    pad.x  = mouseX - pw / 2;
  pad.x = Math.max(0, Math.min(W - pw, pad.x));

  // Stuck ball follows paddle
  if (ball.stuck) { ball.x = pad.x + pw/2; return; }

  // Move ball
  const spd = 5 + (level - 1) * 0.3;
  const mag = Math.hypot(ball.vx, ball.vy) || 1;
  ball.x += (ball.vx / mag) * spd;
  ball.y += (ball.vy / mag) * spd;

  // Wall bounces
  if (ball.x - ball.r < 0)  { ball.x = ball.r;     ball.vx =  Math.abs(ball.vx); }
  if (ball.x + ball.r > W)  { ball.x = W - ball.r; ball.vx = -Math.abs(ball.vx); }
  if (ball.y - ball.r < 0)  { ball.y = ball.r;      ball.vy =  Math.abs(ball.vy); }

  // Paddle bounce
  if (ball.vy > 0 && ball.y+ball.r >= pad.y && ball.y-ball.r <= pad.y+pad.h && ball.x >= pad.x && ball.x <= pad.x+pw) {
    ball.y = pad.y - ball.r;
    const hit = (ball.x - (pad.x + pw/2)) / (pw/2);
    const sp  = Math.hypot(ball.vx, ball.vy);
    ball.vx   = sp * Math.sin(hit * Math.PI/3);
    ball.vy   = -Math.abs(sp * Math.cos(hit * Math.PI/3));
  }

  // Lost ball
  if (ball.y - ball.r > H) {
    lives--;
    hud();
    if (lives <= 0) return endGame();
    resetBall();
    return;
  }

  // Brick collision
  for (const b of bricks) {
    if (!b.alive) continue;
    if (ball.x+ball.r < b.x || ball.x-ball.r > b.x+BW || ball.y+ball.r < b.y || ball.y-ball.r > b.y+BH) continue;
    const oL = (ball.x+ball.r)-b.x, oR = (b.x+BW)-(ball.x-ball.r);
    const oT = (ball.y+ball.r)-b.y, oB = (b.y+BH) -(ball.y-ball.r);
    const m  = Math.min(oL,oR,oT,oB);
    if      (m===oL) { ball.x-=oL; ball.vx=-Math.abs(ball.vx); }
    else if (m===oR) { ball.x+=oR; ball.vx= Math.abs(ball.vx); }
    else if (m===oT) { ball.y-=oT; ball.vy=-Math.abs(ball.vy); }
    else             { ball.y+=oB; ball.vy= Math.abs(ball.vy); }
    b.hits--;
    if (b.hits <= 0) { b.alive = false; score += b.pts; hud(); }
    break;
  }

  // Win check
  if (bricks.every(b => !b.alive)) {
    if (level >= 10) return endGame(true);
    level++;
    wide = !wide;
    buildBricks();
    resetBall();
    hud();
    const wmsgs = [
      `Things are warming up. Level ${level}.`,
      `The walls keep coming. So do you. Level ${level}.`,
      `Level ${level}. Getting closer. Don't stop now.`,
    ];
    showMsg(`LEVEL ${level}`, `${level}`, wmsgs[Math.floor(Math.random() * wmsgs.length)], 'CONTINUE');
  }
}

// ── Draw ──────────────────────────────────────────────────────
function draw() {
  // Background
  ctx.fillStyle = '#050b18';
  ctx.fillRect(0, 0, W, H);
  // Scanlines
  ctx.fillStyle = 'rgba(0,0,0,0.07)';
  for (let y = 0; y < H; y += 4) ctx.fillRect(0, y, W, 2);

  // Bricks
  bricks.forEach(b => {
    if (!b.alive) return;
    ctx.globalAlpha = (b.hits === 1 && COLORS[5] === b.color) ? 0.5 + 0.4*Math.sin(Date.now()*0.01) : 1;
    ctx.fillStyle   = b.color;
    ctx.beginPath(); ctx.roundRect(b.x, b.y, BW, BH, 4); ctx.fill();
    ctx.fillStyle   = 'rgba(255,255,255,0.15)';
    ctx.beginPath(); ctx.roundRect(b.x+2, b.y+2, BW-4, 5, 2); ctx.fill();
    ctx.globalAlpha = 1;
  });

  // Paddle
  const pw = wide ? pad.w * 1.7 : pad.w;
  ctx.fillStyle = wide ? '#00f5ff' : '#9090ff';
  ctx.beginPath(); ctx.roundRect(pad.x, pad.y, pw, pad.h, 7); ctx.fill();

  // Ball
  ctx.fillStyle = '#ffffff';
  ctx.beginPath(); ctx.arc(ball.x, ball.y, ball.r, 0, Math.PI*2); ctx.fill();
}

// ── HUD & Messages ────────────────────────────────────────────
function hud() {
  scoreEl.textContent = score.toLocaleString();
  levelEl.textContent = level;
  livesEl.textContent = (lives > 0 ? lives : 0) + ' / 3';
  hiEl.textContent    = hiScore.toLocaleString();
}

function showMsg(title, icon, sub, btnTxt) {
  playing = false;
  oTitle.textContent = title; oIcon.textContent = icon;
  oSub.innerHTML = sub; startBtn.textContent = btnTxt;
  overlay.classList.remove('hidden');
}

function endGame(won = false) {
  if (score > hiScore) { hiScore = score; localStorage.setItem('bx_hi', hiScore); }
  hud();
  const msgs = ['The angle was off. Try again.','That one got away. The next one won\'t.','Adjust. Come back.'];
  won
    ? showMsg('YOU WIN', 'GG', `<strong>${score.toLocaleString()} pts</strong><br/>Every wall came down.<br/><em style="opacity:.6">Persistence. Precision. That always works.</em>`, 'PLAY AGAIN')
    : showMsg('GAME OVER', 'OUT', `<strong>${score.toLocaleString()} pts</strong><br/><em style="opacity:.6">${msgs[Math.floor(Math.random()*msgs.length)]}</em>`, 'TRY AGAIN');
}

// ── Button ────────────────────────────────────────────────────
startBtn.addEventListener('click', () => {
  if (!playing) { startGame(); return; }
  overlay.classList.add('hidden');
  playing = true;
  loop();
});

// ── Init display ──────────────────────────────────────────────
hiEl.textContent = hiScore.toLocaleString();
