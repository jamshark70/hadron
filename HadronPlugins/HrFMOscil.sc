HrFMOscil : HadronPlugin {
	var wavetables, waveformBts, waveAmps, waveEditors;
	var freqSl, detuneSl, keyscaleSl, ampSl, timescaleSl, envBtn, envGui, carEnv, modGuis;

	*initClass {
		this.addHadronPlugin;
		StartUp.add {
			ServerBoot.add {
				SynthDef('HrFMOscil', { |freq = 440, detune, amp = 0.1, keyscale,
					m0_coarse = 1, m0_fine = 0, m0_level, m0_mul = 1, m0_pan,
					m1_coarse = 1, m1_fine = 0, m1_level, m1_mul = 1, m1_pan,
					bufs = #[0, 0, 0], outBus0, gate = 1, timescale = 1, doneAction = 2|

					var basefreq = 220, freqs, mods, cars, env, eg;
					m0_level = m0_level * basefreq / ((keyscale * freq) + (basefreq * (1 - keyscale)));
					m1_level = m1_level * basefreq / ((keyscale * freq) + (basefreq * (1 - keyscale)));

					env = NamedControl.kr(\env, (0 ! 40).overWrite(Env.adsr.asArray));
					eg = EnvGen.kr(env, gate, timeScale: timescale, doneAction: doneAction);

					freqs = freq * [1, (detune * 0.01).midiratio];
					mods = [
						[m0_coarse, m0_fine, m0_level, m0_mul],
						[m1_coarse, m1_fine, m1_level, m1_mul]
					].collect { |row, i|
						var ratio = row[0] * (row[1] * 0.01).midiratio;
						Osc.ar(bufs[i+1], freqs[i] * ratio, 0, row[2] * row[3], 1)
					};
					cars = Osc.ar(bufs[0], freqs * mods, 0);
					Out.ar(outBus0, Pan2.ar(cars, [m0_pan, m1_pan], amp * eg).sum)
				}).add;
			};
		};
	}

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		var width = 554,
		height = 230;
		^super.new(argParentApp, "HrFMOscil", argIdent, argUniqueID, argExtraArgs, Rect((Window.screenBounds.width - width).rand, (Window.screenBounds.height - height).rand, width, height), 0, 2, argCanvasXY).init
	}

	init {
		window.background = Color.gray(0.7);
		waveAmps = Array.fill(3, { (0 ! 10).put(0, 1) });
		wavetables = Array.fill(3, {
			Buffer.alloc(Server.default, 2048, 1, { |buf|
				buf.sine1Msg(#[1]);
			});
		});
		carEnv = Env(#[0, 1, 1, 0], #[0.05, 0.9, 0.05], 0, 2);

		freqSl = HrEZSlider(window, Rect(2, 2, 270, 20), "freq", \freq, { |view|
			synthInstance.set(\freq, view.value);
		}, initVal: 440, initAction: true);

		detuneSl = HrEZSlider(window, Rect(2, 24, 270, 20), "detune", #[-100, 100], { |view|
			synthInstance.set(\detune, view.value);
		}, initVal: 0, initAction: true);

		keyscaleSl = HrEZSlider(window, Rect(2, 46, 270, 20), "keyscale", nil, { |view|
			synthInstance.set(\keyscale, view.value);
		}, initVal: 0, initAction: true);

		ampSl = HrEZSlider(window, Rect(282, 2, 270, 20), "amp", \amp, { |view|
			synthInstance.set(\amp, view.value);
		}, initVal: 0.1, initAction: true);

		envBtn = Button(window, Rect(282, 46, 270, 20))
		.states_([
			["show carrier envelope", Color.black, Color.gray(0.9)],
			["hide carrier envelope", Color.black, Color(0.8, 1, 0.8)]
		])
		.action_({ |view|
			var win, gui;
			if(view.value > 0 and: { envGui.isNil }) {
				win = Window("carrier envelope", Rect(
					Window.screenBounds.width - 405,
					max(0, Window.screenBounds.height - 350),
					400, 301
				))
				.onClose_({
					envGui = nil;
					view.value = 0;
				});
				gui = HrEnvelopeNodeEditor(win, Rect(2, 2, 396, 270))
				.env_(carEnv)
				.action_({ |view|
					carEnv = view.value;
					synthInstance.set(\env, carEnv);
				})
				.insertAction_({ |view|
					carEnv = view.value;
					// changes to release/loop/num of nodes require new synth
					if(synthInstance.notNil) { this.makeSynth(false) };
				});
				gui.curveAction = gui.action;
				gui.deleteAction = gui.insertAction;
				gui.nodeAction = gui.insertAction;
				win.front;

				envGui = [win, gui];
			} {
				envGui[0].onClose_(nil).close;
				envGui = nil;
				view.value = 0
			};
		});

		modGuis = Array.fill(2, { |i|
			var comp = CompositeView(window, Rect(2 + (280*i), 70, 270, 134))
			.background_(Color(0.9, 0.9 + (0.1*i), 1));
			var env = (
				comp: comp,
				coarseSym: "m%_coarse".format(i).asSymbol,
				fineSym: "m%_fine".format(i).asSymbol,
				levelSym: "m%_level".format(i).asSymbol,
				mulSym: "m%_mul".format(i).asSymbol,
				panSym: "m%_pan".format(i).asSymbol,

				coarse: HrEZSlider(comp, Rect(2, 2, 266, 20), "coarse",
					#[0.5, 10.0, \lin, 0.5, 1], { |view|
						synthInstance.set(env[\coarseSym], view.value);
					}, 1
				),
				fine: HrEZSlider(comp, Rect(2, 24, 266, 20), "fine",
					#[-100, 100, \lin, 1, 0], { |view|
						synthInstance.set(env[\fineSym], view.value);
					}, 0
				),
				level: HrEZSlider(comp, Rect(2, 46, 266, 20), "level",
					#[0.01, 50, \exp], { |view|
						synthInstance.set(env[\levelSym], view.value);
					}, 1
				),
				mul: HrEZSlider(comp, Rect(2, 68, 266, 20), "mul",
					\amp, { |view|
						synthInstance.set(env[\mulSym], view.value);
					}, 1
				),
				pan: HrEZSlider(comp, Rect(2, 90, 266, 20), "pan",
					\bipolar, { |view|
						synthInstance.set(env[\panSym], view.value);
					}, 0
				)
			);
			env
		});

		waveEditors = nil ! 3;
		waveformBts = [
			["carrier", Rect(282, 24, 270, 20), window],  // carrier wave editor
			["modulator 1", Rect(2, 112, 266, 20), modGuis[0][\comp]],  // mod1 wave editor
			["modulator 2", Rect(2, 112, 266, 20), modGuis[1][\comp]]   // mod2 wave editor
		].collect { |triplet, i|
			var win, thisButton;
			thisButton = Button(triplet[2], triplet[1])
			.states_([
				["show % wave".format(triplet[0]), Color.black, Color.gray(0.9)],
				["hide % wave".format(triplet[0]), Color.black, Color(0.8, 1, 0.8)]
			])
			.action_({ |view|
				if(view.value > 0 and: { waveEditors[i].isNil }) {
					waveEditors[i] = [
						win = Window(
							"% wave".format(triplet[0]),
							Rect(
								Window.screenBounds.width - 405,
								max(0, Window.screenBounds.height - (i*150) - 350),
								400, 301
							)
						)
						.onClose_({
							waveEditors[i] = nil;
							thisButton.value = 0;
						}),
						HrKlangEditor(win, win.view.bounds.insetBy(2, 2), waveAmps[i].size)
						.slowAction_(true)
						.value_(waveAmps[i])
						.action_({ |view|
							waveAmps[i] = view.value;
							wavetables[i].sine1(view.value);
						});
					];
					win.front;
				} {
					if(waveEditors[i].notNil) {
						waveEditors[i][0].onClose_(nil).close;
						waveEditors[i] = nil;
					}
				};
			});
		};

		timescaleSl = HrEZSlider(window, Rect(2, 206, 550, 20), "env time scale",
			#[0.01, 20, \exp], { |view|
				synthInstance.set(\timescale, view.value);
			}, 1
		);

		saveGets = [
			{ [freqSl.value, detuneSl.value, keyscaleSl.value, ampSl.value, timescaleSl.value] },
			{
				modGuis.collect { |env|
					#[coarse, fine, level, mul, pan].collect { |name| env[name].value }
				}
			},
			{ waveAmps },
			{ carEnv }
		];
		saveSets = [
			{ |argg|
				[freqSl, detuneSl, keyscaleSl, ampSl, timescaleSl].do { |sl, i|
					if(argg[i].notNil) { sl.valueAction = argg[i] }
				};
			},
			{ |argg|
				modGuis.do { |env, i|
					#[coarse, fine, level, mul, pan].do { |name, j|
						env[name].valueAction = argg[i][j];
					}
				}
			},
			{ |argg|
				waveAmps = argg;
				waveAmps.do { |amps, i| wavetables[i].sine1(amps) };
			},
			{ |argg| if(argg.notNil) { carEnv = argg } }
		];

		modGets.putAll((
			freq: { freqSl.value },
			detune: { detuneSl.value },
			keyscale: { keyscaleSl.value },
			amp: { ampSl.value },
			timescale: { timescaleSl.value }
		));
		modSets.putAll((
			freq: { |argg| synthInstance.set(\freq, argg); defer { freqSl.value = argg } },
			detune: { |argg| synthInstance.set(\detune, argg); defer { detuneSl.value = argg } },
			keyscale: { |argg| synthInstance.set(\keyscale, argg); defer { keyscaleSl.value = argg } },
			amp: { |argg| synthInstance.set(\amp, argg); defer { ampSl.value = argg } },
			timescale: { |argg| synthInstance.set(\timescale, argg); defer { timescaleSl.value = argg } }
		));
		modMapSets.putAll((
			freq: { |argg| defer { freqSl.value = argg } },
			detune: { |argg| defer { detuneSl.value = argg } },
			keyscale: { |argg| defer { keyscaleSl.value = argg } },
			amp: { |argg| defer { ampSl.value = argg } },
			timescale: { |argg| defer { timescaleSl.value = argg } }
		));
		modGuis.do { |env, i|
			#[coarse, fine, level, mul, pan].do { |name|
				var modname = env[(name ++ "Sym").asSymbol];
				modGets.put(modname, { env[name].value });
				modSets.put(modname, { |argg| synthInstance.set(modname, argg); defer { env[name].value = argg } });
				modMapSets.put(modname, { |argg| defer { env[name].value = argg } });
			}
		};

		this.makeSynth;
	}

	synthArgs {
		var mods = Array(20);
		modGuis.do { |env, i|
			#[coarse, fine, level, mul, pan].do { |name|
				mods.add(env[(name ++ "Sym").asSymbol]).add(env[name].value)
			}
		};
		^[
			freq: freqSl.value, detune: detuneSl.value, amp: ampSl.value,
			keyscale: keyscaleSl.value, bufs: wavetables, outBus0: outBusses[0],
			env: carEnv, timescale: timescaleSl.value
		] ++ mods ++ this.getMapModArgs
	}

	updateBusConnections { synthInstance.set(\outBus0, outBusses[0]) }

	// generic synthdef, already made - the stub prevents error
	makeSynthDef {}

	makeSynth { |newSynthDef(true)|
		// it's a little bit dumb that I have to do this, but
		// it's the only way to conditionally not execute something after try
		var shouldPlay = true,
		// and this: don't recall if forkIfNeeded exists in 3.4
		doIt = {
			if(newSynthDef) {
				try {
					this.makeSynthDef;
				} { |err|
					if(err.isKindOf(Exception)) {
						shouldPlay = false;
						err.reportError;
						defer { parentApp.displayStatus(err.errorString, -1) };
					};
				};
			};
			if(shouldPlay) {
				Server.default.sync;
				this.releaseSynth;
				synthInstance = Synth(this.class.name, this.synthArgs, group);
			};
		};
		if(thisThread.isKindOf(Routine)) {
			doIt.value
		} {
			doIt.fork
		}
	}

	cleanUp {
		wavetables.do(_.free);
		defer {
			waveEditors.do { |pair|
				if(pair.notNil) { pair[0].onClose_(nil).close };
			};
			if(envGui.notNil) { envGui[0].onClose_(nil).close };
		};
	}

	hasGate { ^carEnv.releaseNode.notNil }
	polySupport { ^true }
	defName { ^"HrFMOscil" }
}