HrChorus2 : HadronPlugin {
	var <synthInstance;
	var numDelaySlider, predelaySlider, speedSlider, 
	depthSlider, phdiffSlider, preampSlider;
	var numDelays;

	*initClass { this.addHadronPlugin }

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		var bounds = Rect((Window.screenBounds.width - 450).rand, (Window.screenBounds.height - this.height).rand, 450, this.height);

		^super.new(argParentApp, this.name.asString, argIdent, argUniqueID, argExtraArgs, bounds, argNumIns: 2, argNumOuts: 2, argCanvasXY: argCanvasXY).init
	}

	*height { ^150 }

	init {
		helpString = "Stereo chorus. Both of the 2 channels get 'Num delays' delays.";
		this.makeViews;

		saveGets = [
			{ numDelaySlider.value },
			{ predelaySlider.value },
			{ speedSlider.value },
			{ depthSlider.value },
			{ phdiffSlider.value },
			{ preampSlider.value }
		];

		saveSets = [
			{ |val| numDelaySlider.valueAction_(val) },
			{ |val| predelaySlider.valueAction_(val) },
			{ |val| speedSlider.valueAction_(val) },
			{ |val| depthSlider.valueAction_(val) },
			{ |val| phdiffSlider.valueAction_(val) },
			{ |val| preampSlider.valueAction_(val) }
		];

		// numDelays is deliberately left out: shouldn't be automate-able
		(
			predelay: { |val| predelaySlider.valueAction_(val) },
			speed: { |val| speedSlider.valueAction_(val) },
			depth: { |val| depthSlider.valueAction_(val) },
			phdiff: { |val| phdiffSlider.valueAction_(val) },
			preamp: { |val| preampSlider.valueAction_(val) }
		).associationsDo { |assn| modSets.add(assn) };

		(
			predelay: { |val| predelaySlider.value },
			speed: { |val| speedSlider.value },
			depth: { |val| depthSlider.value },
			phdiff: { |val| phdiffSlider.value },
			preamp: { |val| preampSlider.value }
		).associationsDo { |assn| modGets.add(assn) };

		this.makeSynth;
		SkipJack({
			if(numDelays != numDelaySlider.value) { this.makeSynth };
		}, dt: 0.2, name: ("HrChorus2_" ++ uniqueID).asSymbol, clock: AppClock)
	}

	makeViews {
		window.decorator = FlowLayout(window.bounds.moveTo(0, 0));
		numDelaySlider = HrEZSlider(window, 440@20, "Num delays",
			#[1, 8, \lin, 1, 3],
			action: nil,  // updates can be too fast; skipjack it (see init)
			initVal: 3
		);
		predelaySlider = HrEZSlider(window, 440@20, "Predelay",
			#[0.001, 0.1, \exp, 0, 0.01],
			{ |view| synthInstance.set(\predelay, view.value) },
			0.01
		);
		speedSlider = HrEZSlider(window, 440@20, "Speed",
			#[0.01, 8, \exp, 0, 0.05],
			{ |view| synthInstance.set(\speed, view.value) },
			0.05
		);
		depthSlider = HrEZSlider(window, 440@20, "Depth",
			#[0.001, 0.1, \exp, 0, 0.01],
			{ |view| synthInstance.set(\depth, view.value) },
			0.004
		);
		phdiffSlider = HrEZSlider(window, 440@20, "Phase diff",
			#[0, 2pi, \lin, 0, 0],
			{ |view| synthInstance.set(\phdiff, view.value) },
			0
		);
		preampSlider = HrEZSlider(window, 440@20, "Preamp",
			\amp,
			{ |view| synthInstance.set(\preamp, view.value) },
			0.4
		);
		// Of course I can't just do this w/o try... CocoaGUI doesn't support decimals_
		// :-|
		try {
			predelaySlider.numberView.decimals = 3;
			depthSlider.numberView.decimals = 3;
		};
	}

	synthArgs {
		^[
			#[
				inBus0, inBus1, outBus0, outBus1,
				predelay, speed, depth, phdiff, preamp
			],
			inBusses[0..1] ++ outBusses[0..1]
			++ [
				predelaySlider.value,
				speedSlider.value,
				depthSlider.value,
				phdiffSlider.value,
				preampSlider.value
			]
		].flop.flat
	}

	makeSynth {
		var playFunc = {
			if(synthInstance.notNil) {
				synthInstance.release;
			};
			synthInstance = Synth("hrChorus2_" ++ uniqueID, this.synthArgs,
				target: group);
		};
		if(numDelays != numDelaySlider.value) {
			numDelays = numDelaySlider.value;
			fork {
				SynthDef("hrChorus2_" ++ uniqueID, { |inBus0, inBus1, outBus0, outBus1,
					predelay, speed, depth, ph_diff, preamp, gate = 1|
					var in, sig, mods, fx;
					in = In.ar([inBus0, inBus1], 1);
					mods = { |i|
						SinOsc.kr(speed * rrand(0.9, 1.1), ph_diff * i, depth, predelay);
					} ! (numDelays * 2);
					sig = DelayC.ar(in * preamp, 0.5, mods);
					fx = Mix(sig.clump(2));
					fx = XFade2.ar(in, fx,
						EnvGen.kr(
							Env(#[-1, 1, -1], #[0.1, 0.1], releaseNode: 1),
							gate,
							doneAction: 2
						)
					);
					Out.ar(outBus0, fx[0]);
					Out.ar(outBus1, fx[1]);
				}).add;
				Server.default.sync;
				playFunc.value;
			}
		} {
			playFunc.value
		};
	}

	updateBusConnections {
		synthInstance.set(*this.synthArgs);
	}
	cleanUp {
		SkipJack.stop(("HrChorus2_" ++ uniqueID).asSymbol)
	}
}


HrFlanger : HrChorus2 {
	var fbSlider, delaytime, feedback,
	// choose flanger (0) or phaser (1)
	typeMenu, typeIndex = 0,
	log001;

	*initClass { this.addHadronPlugin }
	*height { ^200 }

	init {
		super.init;
		log001 = log(0.001);

		saveGets = saveGets.add({ fbSlider.value })
		.add({ typeMenu.value });

		saveSets = saveSets.add({ |val| fbSlider.valueAction_(val) })
		.add({ |val| typeMenu.valueAction_(val) });
	}

	makeViews {
		super.makeViews;
		fbSlider = HrEZSlider(window, 440@20, "Feedback",
			#[0, 1, \lin, 0, 0.1],
			{ |view|
				feedback = view.value;
				synthInstance.set(\decay, this.decay)
			},
			feedback = 0.01
		);
		typeMenu = PopUpMenu(window, 150@20)
		.items_(["Flanger", "Phaser"])
		.value_(0)
		.action_({ |view|
			var shouldMakeSynth = typeIndex != view.value;
			typeIndex = view.value;
			if(shouldMakeSynth) { this.makeSynth };
		});
		delaytime = predelaySlider.value;
		predelaySlider.action = predelaySlider.action.addFunc({ |view|
			delaytime = view.value;
			synthInstance.set(\decay, this.decay);
		});
	}

	// sc_CalcFeedback: feedback = exp(log001 * delaytime / decaytime)
	// so, decaytime = ...
	decay {
		^log001 * delaytime / log(feedback)
	}

	synthArgs {
		^super.synthArgs ++ [decay: this.decay]
	}

	makeSynth {
		var playFunc = {
			if(synthInstance.notNil) {
				synthInstance.release;
			};
			synthInstance = Synth("hrChorus2_" ++ uniqueID, this.synthArgs,
				target: group);
		};
		if(numDelays != numDelaySlider.value) {
			numDelays = numDelaySlider.value;
			fork {
				SynthDef("hrChorus2_" ++ uniqueID, { |inBus0, inBus1, outBus0, outBus1,
					predelay, speed, depth, decay, ph_diff, preamp, gate = 1|
					var in, sig, mods, fx;
					in = In.ar([inBus0, inBus1], 1);
					mods = { |i|
						SinOsc.kr(speed * rrand(0.9, 1.1), ph_diff * i, depth, predelay);
					} ! (numDelays * 2);
					sig = [CombC, AllpassC][typeIndex].ar(in * preamp, 0.5, mods, decay);
					fx = Mix(sig.clump(2));
					fx = XFade2.ar(in, fx,
						EnvGen.kr(
							Env(#[-1, 1, -1], #[0.1, 0.1], releaseNode: 1),
							gate,
							doneAction: 2
						)
					);
					Out.ar(outBus0, fx[0]);
					Out.ar(outBus1, fx[1]);
				}).add;
				Server.default.sync;
				playFunc.value;
			}
		} {
			playFunc.value
		};
	}
}