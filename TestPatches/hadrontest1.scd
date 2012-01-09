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


f = h.alivePlugs[2];
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