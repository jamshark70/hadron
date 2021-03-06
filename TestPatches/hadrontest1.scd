Help.rebuildTree;

HrDIYSynth
HadronPlugin

// try swing
GUI.swing;
SwingOSC.default.boot;

SwingOSC.default.boot;
ShutDown.add { SwingOSC.default.quit };
GUI.swing;

SwingOSC.default.quit;

GUI.qt;

Error.debug = true;
Error.debug = false;


s.boot;

(
SynthDef(\analog, { |outBus0, outBus1, freq = 440, dtune = 1.003, ffreq = 2000, rq = 0.1, amp = 0.1, gate = 1|
	var sig = Saw.ar(freq * [1, dtune], amp).sum;
	sig = RLPF.ar(sig, ffreq, rq);
	Out.ar([outBus0, outBus1], sig * EnvGen.kr(Env.adsr, gate, doneAction:2));
}, metadata: (
	specs: (
		freq: \freq.asSpec,
		dtune: [0.95, 0.95.reciprocal, \exp, 0, 1.003].asSpec,
		ffreq: \freq.asSpec,
		rq: [1, 0.05, \exp].asSpec,
		amp: \amp.asSpec
	)
)).add;
)

~debugAddr = DebugNetAddr("127.0.0.1", 57110);
~saveAddr = NetAddr("127.0.0.1", 57110);

s.addr = ~debugAddr;

s.addr = ~saveAddr;

h = Hadron.new;

HadronStateLoad(h).loadState("~/share/SC/hadron.git/TestPatches/presetmorph-mod.hadron".standardizePath);

HrCtlMod.pollRate = 14;

s.sendMsg(\n_trace, 1009)
SynthDef.findRespondingMethodFor(\printOn).filenameSymbol

s.sendMsg(\n_trace, 1009)
s.sendMsg(\n_trace, 1007)

HrSimpleModulator
Help.rebuildTree;

HrCodeSequencer
HadronPlugin

// are array args properly ignored?

SynthDef(\arraytest, { |freq = #[220, 330, 440], amp = 0.1, outBus0, outBus1|
	Out.ar([outBus0, outBus1], SinOsc.ar(freq).sum * amp);
}, metadata: (
	specs: (
		amp: \amp.asSpec
	)
)).add;

Hadron.new;


// shift-click not working in qt
GUI.swing;
GUI.qt;

w = Window(\test, Rect(800, 200, 150, 100));
u = UserView(w, w.view.bounds.insetBy(2, 2))
.mouseDownAction_({ |... args| args.debug("mousedown") });
w.front;


f = h.alivePlugs[0];
f.slotAt(\synthInstance).trace
f.slotAt(\badValueSynth).trace
f.slotAt(\prOutBus)

KrBusWatcher.register(f.slotAt(\prOutBus));

f.slotAt(\prOutBus).addDependant { |... args| args.postln };
f.slotAt(\prOutBus).releaseDependants;

KrBusWatcher.unregister(f.slotAt(\prOutBus));

f.slotAt(\postOpText).value.postcs
f.slotAt(\postOpText).value.asArray

f.slotAt(\synthInstance).set(\t_trig, 1);


// audioenv doesn't pass trigger if env is still going?
// it DOES pass the trigger - HrDIYSynth doesn't set the inbusses right F-U-U-U-U-U-U-
a = {
	Poll.ar(In.ar(f.outBusses[1], 1), In.ar(f.outBusses[0], 1));
	0
}.play(addAction: \addToTail);

f.slotAt(\synthInstance).set(\t_trig, 1);

s.sendMsg(\n_trace, 1006);

f = h.alivePlugs.first;
f.slotAt(\availParams)

f = h.alivePlugs.last;
f.name

f.insp

f.slotAt(\outerWindow).bounds
f.slotAt(\window).bounds

w = f.slotAt(\outerWindow);
w.view.children[5].bounds
w.view.children[0].bounds

w.bounds

w.view.children[5].string


p = h.alivePlugs[2];
p.slotAt(\modMapSets)[\surfaceXY].def.dumpByteCodes

f = { |time, replyAddr, recvPort, msg|
	if(msg[0] != '/status.reply') { msg.postln };
};

thisProcess.addOSCFunc(f);
thisProcess.removeOSCFunc(f);

p.uniqueID

h.alivePlugs[3].slotAt(\synthInstance).trace;


// checking a math change in hrpresetmorph
// it's ok in this test case... F-U-U-U-U-U-

d = Point(10, 10).rho;
c = Env([0, 0.05, 0.1, 0.9, 1], [3, 0.5, 0.85, 0.15], [0, 0, 0, -4]).asSignal(d).asArray.reverse;
c.plot;

e = Env(
	#[0, 0.05, 0.1, 0.9, 1],
	#[3, 0.5, 0.85, 0.15].normalizeSum,
	#[0, 0, 0, -4]
);

e[(1.0, 0.99 .. 0.0)].plot;

x = Array.fill(5, { Point(10.0.rand, 10.0.rand) });
y = Point(10.0.rand, 10.0.rand);

(
var tempDist, tempSum = 0;
x.do { |pt|
	tempDist = pt.dist(y).debug("tempDist");
	tempSum = (tempSum + ((1 - (tempDist/d)) * c[tempDist]).debug("adding")).debug("sum");
};
)

tempDist: 3.4914291369139
adding: 0.069595774230166
sum: 0.069595774230166
tempDist: 5.1311513395744
adding: 0.029089921080472
sum: 0.098685695310639
tempDist: 3.5633873474681
adding: 0.069125571865475
sum: 0.16781126717611
tempDist: 2.3861908698075
adding: 0.29163807878631
sum: 0.45944934596242
tempDist: 4.4470777532062
adding: 0.039877479452245
sum: 0.49932682541467

tempDist: 3.4914291369139
normalized, flipped: 0.75311867812559
adding: 0.066954814972337
sum: 0.066954814972337
tempDist: 5.1311513395744
normalized, flipped: 0.63717280924925
adding: 0.030449189163493
sum: 0.097404004135831
tempDist: 3.5633873474681
normalized, flipped: 0.7480304642611
adding: 0.064789692892929
sum: 0.16219369702876
tempDist: 2.3861908698075
normalized, flipped: 0.83127082547537
adding: 0.27145879513622
sum: 0.43365249216498
tempDist: 4.4470777532062
normalized, flipped: 0.68554411642441
adding: 0.040100801897753
sum: 0.47375329406273


(
var tempDist, tempSum = 0;
x.do { |pt|
	tempDist = pt.dist(y).debug("tempDist");
	tempDist = 1 - (tempDist / d);
	tempDist.debug("normalized, flipped");
	tempSum = (tempSum + (tempDist * e[tempDist]).debug("adding")).debug("sum");
};
)



();
// convert hrwrapsynth parm lists

// nope, not necessary, i didn't change the parm list format
x = "637990.447400nilList[  ]0.529200nilList[  ]0.666600nilList[  ]0.768600nilList[  ]0.316200nilList[  ]1".split(31.asAscii);
y = SynthDescLib.at(\analog);
z = y.controls[2..];

do((1, 4 .. x.size-2)) { |i, j|
	x[i] = y.metadata.specs[z[j].name.asSymbol].map(x[i].asFloat).asCompileString;
};

x.join(31.asAscii);


// but I did change the dictionary style for hrpresetmorph...

// [ [ '1', Rect(41.0, 28.0, 12.0, 20.0), Color(0.85053963661194, 0.82543201446533, 0.44811108112335) ], [ '2', Rect(405.0, 211.0, 12.0, 20.0), Color(0.88997089862823, 0.38712551593781, 0.76856331825256) ], [ '3', Rect(19.0, 413.0, 12.0, 20.0), Color(0.50058574676514, 0.57714071273804, 0.35300996303558) ] ][ [ 1874922887, 'ffreq' ], [ 1874922887, 'freq' ], [ 1874922887, 'rq' ] ][ [ 1874922887, 'amp' ], [ 1874922887, 'dtune' ], [ 1874922887, 'ffreq' ], [ 1874922887, 'freq' ], [ 1874922887, 'rq' ], [ 956940551, 'level' ] ];

x = Dictionary[ ('1' -> Dictionary[ (956940551 -> Dictionary[ ('level' -> 0.18519999086857) ]), (1874922887 -> Dictionary[ ('amp' -> 0.31622776837292), ('dtune' -> 0.52920001467683), ('freq' -> 0.44747422694074), ('rq' -> 0.76862178186611), ('ffreq' -> 0.66666666666667) ]) ]), ('2' -> Dictionary[ (956940551 -> Dictionary[ ('level' -> 0.18519999086857) ]), (1874922887 -> Dictionary[ ('amp' -> 0.31622776837292), ('dtune' -> 0.41569998860359), ('freq' -> 0.30339998006821), ('rq' -> 0.93259996175766), ('ffreq' -> 0.53929996490479) ]) ]), ('3' -> Dictionary[ (956940551 -> Dictionary[ ('level' -> 0.18519999086857) ]), (1874922887 -> Dictionary[ ('amp' -> 0.31622776837292), ('dtune' -> 0.83149999380112), ('freq' -> 0.6628999710083), ('rq' -> 0.44939997792244), ('ffreq' -> 0.82019996643066) ]) ]) ];

x.keysValuesDo { |key, preset|
	preset.keysValuesDo { |uniqueID, parms|
		if(uniqueID == 1874922887) {
			parms.keysValuesDo { |key, value|
				parms[key] = y.metadata.specs[key].map(value);
			}
		}
	}
};

x.postcs; ""


h.alivePlugs[2].insp



// connections wrong on load?

h = Hadron.new;

{
	HadronStateLoad(h)
	.loadState("~/share/SC/hadron.git/TestPatches/analog1.hadron".standardizePath);

	0.8.wait;

	h.alivePlugs.do { |plug|
		plug.slotAt(\synthInstance).tryPerform(\trace)
	};
}.fork(AppClock);

h.alivePlugs[1].slotAt(\badValueSynth).trace;

h.alivePlugs[0].inBusses

s.audioBusAllocator.debug


// multictlmod audio out
f = h.alivePlugs.detect { |plug| plug.class == HrMultiCtlMod };
f.synthInstance.trace

f.slotAt(\badValueSynth).trace

h.alivePlugs;  // List[ a HrMultiCtlMod, a HrDIYSynth, a HrDIYSynth, a HrStereoMixer, a HrDAC ]

h.alivePlugs[1].slotAt(\synthInstance).trace



// POC for poly pattern player - good!
// load "analog1.hadron"
p = h.alivePlugs.detect({ |plug| plug.isMemberOf(HrWrapSynth) });
h.alivePlugs[0].polyMode = false; // should be no op
h.alivePlugs[0].polyMode = true;  // should be error

p.polyMode = true;
p.polyMode = false;  // test...

p.releaseSynth;

q = Pchain(
	Pbindef(\x, \dur, Pexprand(0.08, 0.3, inf), \freq, Pexprand(140, 900, inf)),
	Pfunc({ |ev|
		ev.put(\group, p.group).put(\instrument, p.slotAt(\synthDesc).name)
		.putPairs(p.synthArgs)
	})
).play;

q.stop


p.polyMode = false;

p = h.alivePlugs.detect({ |plug| plug.isMemberOf(HrFMOscil) });
p = h.alivePlugs.detect({ |plug| plug.isMemberOf(HrOscil) });
p.polyMode = true;

n = if(p.isMemberOf(HrFMOscil)) {
	p.class.name
} {
	p.class.name ++ p.uniqueID
};

q = Pchain(
	Pbindef(\x, \dur, Pexprand(0.08, 0.3, inf), \freq, Pexprand(140, 900, inf)),
	Pfunc({ |ev|
		ev.put(\group, p.group).put(\instrument, n)
		.putPairs(p.synthArgs)
	})
).play;

q.stop;

p.synthArgs



// test delay synthdef
(
SynthDef("HrDelay", { | /*inBus0, outBus0,*/ outbus, ffreq = 8000, mix = 0.5,
	bufs = #[0, 1],
	feedback = #[0, 0], delaytime = #[0.1, 0.1],
	mulL = #[1, 0], mulR = #[0, 1]|

	var sig = In.ar(/*inBus0*/ outbus, 2),
	fbsig = LocalIn.ar(2) * feedback,
	delay;

	sig = sig + fbsig;
	sig = [mulL, mulR].collect { |mul, i| mul * sig[i] }.sum;

	delay = BufDelayL.ar(bufs, sig, delaytime - ControlDur.ir);
	LocalOut.ar(LPF.ar(delay, ffreq));

	delay = XFade2.ar(sig, delay, mix.madd(2, -1));
	// Out.ar(outBus0, delay);
	ReplaceOut.ar(outbus, delay);
}).add;
)

m = MixerChannel(\test, s, 2, 2);

b.free;
b = Buffer.allocConsecutive(2, s, 65536, 1);

a = m.playfx("HrDelay", [bufs: b, delaytime: #[0.5, 0.5]]);

a.trace;
a.free;

SynthDef(\shortblip, { |out|
	Out.ar(out, SinOsc.ar(#[440, 880], 0, EnvGen.kr(Env.perc(0.01, 0.1), doneAction: 2)))
}).add;

// m.play((freq: #[440, 880], pan: #[-1, 1], sustain: 0.05, amp: 0.8));
m.play((instrument: \shortblip));
s.sendBundle(0.205, [\n_trace, a.nodeID]);
s.sendBundle(0.705, [\n_trace, a.nodeID]);



SynthDef("HrDelay", { | /*inBus0, outBus0,*/ outbus, ffreq = 8000, mix = 0.5,
	bufs = #[0, 1],
	feedback = #[0, 0], delaytime = #[0.1, 0.1],
	mulL = #[1, 0], mulR = #[0, 1]|

	var sig = InFeedback.ar(/*inBus0*/ outbus, 2),
	fbsig = LocalIn.ar(2) * feedback,
	delay;

	sig = sig + fbsig;
	sig = [mulL, mulR].collect { |mul, i| mul * sig[i] }.sum;

	delay = BufDelayL.ar(bufs, sig, delaytime - ControlDur.ir);
	LocalOut.ar(LPF.ar(delay, ffreq));

	delay = XFade2.ar(sig, delay, mix.madd(2, -1));
	// Out.ar(outBus0, delay);
	ReplaceOut.ar(outbus, delay);
}).draw;

Quarks.gui;