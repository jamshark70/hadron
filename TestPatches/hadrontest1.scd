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


s.boot;

SynthDef(\analog, { |outBus0, outBus1, freq = 440, dtune = 1.003, ffreq = 2000, rq = 0.1, amp = 0.1|
	var sig = Saw.ar(freq * [1, dtune], amp).sum;
	sig = RLPF.ar(sig, ffreq, rq);
	Out.ar([outBus0, outBus1], sig);
}, metadata: (
	specs: (
		freq: \freq.asSpec,
		dtune: [0.95, 0.95.reciprocal, \exp, 0, 1.003].asSpec,
		ffreq: \freq.asSpec,
		rq: [1, 0.05, \exp].asSpec,
		amp: \amp.asSpec
	)
)).add;

~debugAddr = DebugNetAddr("127.0.0.1", 57110);
~saveAddr = NetAddr("127.0.0.1", 57110);

s.addr = ~debugAddr;
// s.addr = ~saveAddr;

h = Hadron.new;

HadronStateLoad(h).loadState("~/share/SC/hadron.git/TestPatches/presetmorph-mod.scd".standardizePath);

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
