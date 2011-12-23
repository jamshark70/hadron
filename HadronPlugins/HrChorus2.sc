HrChorus2 : HadronPlugin {
	var <synthInstance;
	var numDelaySlider, predelaySlider, speedSlider, depthSlider, phdiffSlider, preampSlider;
	var numDelays;

	*initClass { this.addHadronPlugin }

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|

// fix size

		var bounds = Rect((Window.screenBounds.width - 450).rand, (Window.screenBounds.height - 400).rand, 450, 400);

		^super.new(argParentApp, this.name.asString, argIdent, argUniqueID, argExtraArgs, bounds, argNumIns: 2, argNumOuts: 2, argCanvasXY: argCanvasXY).init
	}

	init {
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
	}

	// makeViews {
	// 	numDelaySlider
	// 	predelaySlider
	// 	speedSlider
	// 	depthSlider
	// 	phdiffSlider
	// 	preampSlider
	// }

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
					predelay, speed, depth, ph_diff, preamp, wetGate = 1|
					var in, sig, mods, fx;
					in = In.ar([inBus0, inBus1], 2);
					mods = { |i|
						SinOsc.kr(speed * rrand(0.9, 1.1), ph_diff * i, depth, predelay);
					} ! (numDelays * 2);
					sig = DelayC.ar(in * preamp, 0.5, mods);
					fx = Mix(sig.clump(2));
					fx = XFade2.ar(in, fx,
						EnvGen.kr(
							Env(#[-1, 1, -1], #[0.1, 0.1], releaseNode: 1),
							wetGate,
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


/*
Instr([\busfx, \chorus2], { arg bus, numInChan, numOutChan, numDelays, predelay, speed, depth, ph_diff, preamp;
	var in, sig, mods;
	in = In.ar(bus, numInChan) * preamp;
	mods = { |i|
		SinOsc.kr(speed * rrand(0.9, 1.1), ph_diff * i, depth, predelay);
	} ! (numDelays * numOutChan);
	sig = DelayC.ar(in, 0.5, mods);
	Mix(sig.clump(numOutChan))
}, [\audiobus, \numChannels, \numChannels, \numChannels, [0.0001, 0.2, \exponential, 0, 0.001], [0.001, 10, \exponential], [0.0001, 0.25, \exponential], [0, 2*pi], [0.1, 10, \exp, 0, 1]]);
*/