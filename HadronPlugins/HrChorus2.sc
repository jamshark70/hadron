HrChorus2 : HadronPlugin {
	var <synthInstance;
	var numDelaySlider, predelaySlider, speedSlider, 
	depthSlider, phdiffSlider, preampSlider;
	var numDelays;

	*initClass { this.addHadronPlugin }

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		var bounds = Rect((Window.screenBounds.width - 450).rand, (Window.screenBounds.height - 150).rand, 450, 150);

		^super.new(argParentApp, this.name.asString, argIdent, argUniqueID, argExtraArgs, bounds, argNumIns: 2, argNumOuts: 2, argCanvasXY: argCanvasXY).init
	}

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
		numDelaySlider = EZSlider(window, 440@20, "Num delays",
			#[1, 8, \lin, 1, 3],
			action: nil,  // updates can be too fast; skipjack it (see init)
			initVal: 3
		);
		predelaySlider = EZSlider(window, 440@20, "Predelay",
			#[0.001, 0.1, \exp, 0, 0.01],
			{ |view| synthInstance.set(\predelay, view.value) },
			0.01
		);
		// Of course I can't just do this w/o try... CocoaGUI doesn't support decimals_
		// :-|
		try {
			predelaySlider.numberView.decimals = 3;
		};
		speedSlider = EZSlider(window, 440@20, "Speed",
			#[0.01, 8, \exp, 0, 0.05],
			{ |view| synthInstance.set(\speed, view.value) },
			0.05
		);
		depthSlider = EZSlider(window, 440@20, "Depth",
			#[0.001, 0.1, \exp, 0, 0.01],
			{ |view| synthInstance.set(\depth, view.value) },
			0.004
		);
		phdiffSlider = EZSlider(window, 440@20, "Phase diff",
			#[0, 2pi, \lin, 0, 0],
			{ |view| synthInstance.set(\phdiff, view.value) },
			0
		);
		preampSlider = EZSlider(window, 440@20, "Preamp",
			\amp,
			{ |view| synthInstance.set(\preamp, view.value) },
			0.4
		);
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
