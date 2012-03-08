HrPan : HadronPlugin {
	var monoButton, panSlider;

	*initClass {
		this.addHadronPlugin;
		StartUp.add {
			ServerBoot.add {
				SynthDef('HrPan', { |inBus0, outBus0, monoIn = 0, pan = 0|
					var sig = InFeedback.ar(inBus0, 2);
					sig = XFade2.ar(
						Balance2.ar(sig[0], sig[1], pan),
						Pan2.ar(sig.sum, pan),
						Lag.kr(monoIn, 0.05) * 2 - 1
					);
					Out.ar(outBus0, sig);
				}).add;
			};
		};
	}

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		var bounds = Rect((Window.screenBounds.width - 350).rand, (Window.screenBounds.height - 70).rand, 350, 70);

		^super.new(argParentApp, this.name.asString, argIdent, argUniqueID, argExtraArgs, bounds, argNumIns: 2, argNumOuts: 2, argCanvasXY: argCanvasXY).init
	}

	init {
		monoButton = Button(window, Rect(10, 10, 150, 20))
		.states_([["Stereo-to-stereo"], ["Mono-to-stereo"]])
		.action_({ |view|
			synthInstance.set(\monoIn, view.value);
		});

		panSlider = HrEZSlider(window, Rect(10, 40, 330, 20), "pan", \bipolar, { |view|
			synthInstance.set(\pan, view.value);
		}, 0, labelWidth: 40);

		saveGets = [
			{ monoButton.value },
			{ panSlider.value }
		];

		saveSets = [
			{ |argg| monoButton.valueAction_(argg) },
			{ |argg| panSlider.valueAction_(argg) }
		];

		modGets.put(\pan, { panSlider.value });
		modSets.put(\pan, { |argg| synthInstance.set(\pan, argg); defer { panSlider.value_(argg) } });
		modMapSets.put(\pan, { |argg| defer { panSlider.value_(argg) } });

		synthInstance = Synth('HrPan', [
			inBus0: inBusses[0], outBus0: outBusses[0]
		] ++ this.getMapModArgs, group, \addToHead);
	}

	updateBusConnections {
		synthInstance.set(\inBus0, inBusses[0], \outBus0, outBusses[0])
	}

	cleanUp {}
}


HrFilter : HadronPlugin {
	var filtType, clipType, filtTypes, clipTypes, filtMenu, clipMenu;
	var preAmpSlider, postAmpSlider, params;

	*initClass {
		this.addHadronPlugin;
		StartUp.add {
			Spec.specs[\hadronrq] = ControlSpec(1.0, 0.01, \exp, 0, 1);
			Spec.specs[\hadrondecaytime] = ControlSpec(0.01, 10, \exp, 0, 0.1);
			Spec.specs[\hadrondb] = ControlSpec(-20, 20, \lin, 0, 0);
			Spec.specs[\hadronmooggain] = ControlSpec(0, 4);

			Library.put('HrFilter', \filtTypes, [
				// arrays are pairs: param name, spec name
				['LPF': #[freq, freq]],
				['HPF': #[freq, freq]],
				['RLPF': #[freq, freq, rq, hadronrq]],
				['RHPF': #[freq, freq, rq, hadronrq]],
				['BPF': #[freq, freq, rq, hadronrq]],
				['MoogFF': #[freq, freq, gain, hadronmooggain]],
				['Ringz': #[freq, freq, decaytime, hadrondecaytime]],
				['MidEQ': #[freq, freq, rq, hadronrq, db, hadrondb]]
			]);
		};
	}

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		var height = this.height,
		bounds = Rect((Window.screenBounds.width - 350).rand, (Window.screenBounds.height - height).rand, 350, height);

		^super.new(argParentApp, this.name.asString, argIdent, argUniqueID, argExtraArgs, bounds, argNumIns: this.numInputs, argNumOuts: this.numInputs, argCanvasXY: argCanvasXY).init
	}
	*height { ^190 }
	*numInputs { ^2 }

	init {
		var paramComp;
		var fixParamViews = {
			params.do { |sl, i|
				var argname = filtTypes[filtType][1][i * 2];
				if(argname.notNil) {
					sl.visible_(true).label_(argname).spec_(filtTypes[filtType][1][i * 2 + 1]);
					if(paramNotInited[i]) {
						sl.value = sl.spec.default;
						paramNotInited[i] = false;
					} {
						sl.value = sl.spec.constrain(sl.value);
					};
				} {
					sl.visible = false;
				}
			};
		};
		var paramNotInited = true ! 3;

		filtTypes = Library.at(this.class.name, \filtTypes);
		clipTypes = #[none, distort, softclip, tanh];
		
		filtType = 0;
		StaticText(window, Rect(10, 10, 60, 20)).string_("filter");
		filtMenu = PopUpMenu(window, Rect(80, 10, 90, 20))
		.items_(filtTypes.collect(_[0]))
		.action_({ |view|
			filtType = view.value;
			fixParamViews.defer;
			this.makeSynth;
		});

		clipType = 0;
		StaticText(window, Rect(180, 10, 60, 20)).string_("clip");
		clipMenu = PopUpMenu(window, Rect(250, 10, 90, 20))
		.items_(clipTypes)
		.action_({ |view|
			clipType = view.value;
			this.makeSynth;
		});

		preAmpSlider = HrEZSlider(window, Rect(10, 40, 330, 20), "preamp", \hadrondb, { |view|
			synthInstance.set(\preamp, view.value.dbamp)
		});

		paramComp = CompositeView(window, Rect(5, 65, 340, 90))
		.background_(Color(0.8, 1, 0.8));
		params = Array.fill(3, { |i|
			var sl = HrEZSlider(paramComp, Rect(5, 5 + (30 * i), 330, 20), "", nil, { |view|
				var argname = filtTypes[filtType][1][i * 2];
				if(argname.notNil) { synthInstance.set(argname, view.value) };
			});
			if(i == 2) { sl.visible = false };
			sl
		});

		postAmpSlider = HrEZSlider(window, Rect(10, 160, 330, 20), "postamp", \hadrondb, { |view|
			synthInstance.set(\postamp, view.value.dbamp);
		});
		
		saveGets = [
			{ filtType },
			{ clipType },
			{ preAmpSlider.value },
			{ postAmpSlider.value },
			{ params.collect(_.value) }
		];

		saveSets = [
			{ |argg|
				filtType = argg;
				defer { filtMenu.value = argg; fixParamViews.value };
				this.makeSynth;
			},
			{ |argg|
				clipType = argg;
				defer { clipMenu.value = argg };
				this.makeSynth;
			},
			{ |argg| preAmpSlider.valueAction = argg },
			{ |argg| postAmpSlider.valueAction = argg },
			{ |argg| params.do({ |sl, i| sl.valueAction = argg[i] }) }
		];

		modGets = (
			preamp: { preAmpSlider.value },
			postamp: { postAmpSlider.value }
		);
		params.do { |sl, i| modGets.put(("param" ++ i).asSymbol, { sl.value }) };

		modSets = (
			preamp: { |argg| synthInstance.set(\preamp, argg.dbamp); defer { preAmpSlider.value = argg } },
			postamp: { |argg| synthInstance.set(\postamp, argg.dbamp); defer { postAmpSlider.value = argg } }
		);
		params.do { |sl, i| modSets.put(("param" ++ i).asSymbol, { |argg|
			var argname = filtTypes[filtType][1][i * 2];
			if(argname.notNil) { synthInstance.set(argname, argg) };
			defer { sl.value = argg };
		}) };

		modMapSets = (
			preamp: { |argg| defer { preAmpSlider.value = argg } },
			postamp: { |argg| defer { postAmpSlider.value = argg } }
		);
		params.do { |sl, i| modMapSets.put(("param" ++ i).asSymbol, { |argg| defer { sl.value = argg } }) };

		fixParamViews.value;
		this.makeSynth;
	}

	releaseSynth { synthInstance.free; synthInstance = nil; }

	synthArgs {
		var parmValues = Array(6);
		filtTypes[filtType][1].pairsDo { |name, spec, i|
			parmValues.add(name).add(params[i div: 2].value);
		};
		^[inBus0: inBusses[0], outBus0: outBusses[0],
			preamp: preAmpSlider.value.dbamp, postamp: postAmpSlider.value.dbamp,
		] ++ parmValues ++ this.getMapModArgs
	}

	makeSynthDef {
		SynthDef("HrFilter" ++ uniqueID, { |inBus0, outBus0, preamp = 1, postamp = 1|
			var parmValues = Array(3),
			sig = InFeedback.ar(inBus0, 2);
			filtTypes[filtType][1].pairsDo { |name, spec|
				parmValues.add(NamedControl(name, spec.asSpec.default, lags: 0.05, fixedLag: true))
			};
			sig = filtTypes[filtType][0].asClass.ar(sig * preamp, *parmValues);
			if(clipType > 0) {
				sig = sig.perform(clipTypes[clipType]);
			};
			Out.ar(outBus0, sig * postamp);
		}).add;
	}

	mapModCtl { |paramName, ctlBus|
		var node;
		paramName = paramName.asString;
		if(paramName.beginsWith("param")) {
			paramName = filtTypes[filtType][1][paramName[5..].asInteger * 2];
		};
		if(ctlBus == -1 or: { ctlBus.tryPerform(\index) == -1 }) {
			mappedMods.removeAt(paramName.asSymbol);
		} {
			mappedMods.put(paramName.asSymbol, ctlBus);
		};
		if((node = this.tryPerform(\synthInstance)).notNil) {
			node.map(paramName, ctlBus);
		};
	}
	getMapModArgs {
		var result = Array(mappedMods.size * 2);
		mappedMods.keysValuesDo { |param, bus|
			param = param.asString;
			if(param.beginsWith("param")) {
				param = filtTypes[filtType][1][param[5..].asInteger];
			};
			case { bus.isNumber } {
				result.add(param).add(("c" ++ bus).asSymbol)
			}
			{ bus.isKindOf(Bus) } {
				result.add(param).add(bus.asMap)
			}
			// else, invalid map - ignore
		};
		^result
	}

	updateBusConnections {
		synthInstance.set(\inBus0, inBusses[0], \outBus0, outBusses[0])
	}

	cleanUp {}
}

// not because HrLFO is a kind of filter
// but because it also needs variable parameters
// and I want to exploit the logic from HrFilter
HrLFO : HrFilter {
	var spec, specEditor, prOutBus, freqFromInput,
	modControl, pollRate, pollRateView, replyID, watcher, isMapped = false, startButton;

	*initClass {
		this.addHadronPlugin;
		StartUp.add {
			Spec.specs.put(\hadronlofreq, [0.02, 100, \exp, 0, 1].asSpec);
			Spec.specs.put(\hadron2pi, [0, 2pi].asSpec);
			Spec.specs.put(\hadron0to2, [0, 2].asSpec);
			Spec.specs.put(\hadron0to4, [0, 4].asSpec);
			Spec.specs.put(\hadronpwidth, [0, 1, \lin, 0, 0.5].asSpec);
			Library.put('HrLFO', \filtTypes, [
				// arrays are pairs: param name, spec name
				['SinOsc': #[freq, hadronlofreq, phase, hadron2pi]],
				['LFSaw': #[freq, hadronlofreq, phase, hadron0to2]],
				['LFTri': #[freq, hadronlofreq, phase, hadron0to4]],
				['LFPulse': #[freq, hadronlofreq, phase, unipolar, width, hadronpwidth]],
				['LFDNoise0': #[freq, hadronlofreq]],
				['LFDNoise1': #[freq, hadronlofreq]],
				['LFDNoise3': #[freq, hadronlofreq]]
			])
		};
	}
	*height { ^245 }
	*numInputs { ^1 }

	init {
		var paramComp, specErrResp;
		var fixParamViews = {
			params.do { |sl, i|
				var argname = filtTypes[filtType][1][i * 2];
				if(argname.notNil) {
					sl.visible_(true).label_(argname).spec_(filtTypes[filtType][1][i * 2 + 1]);
					if(paramNotInited[i]) {
						sl.value = sl.spec.default;
						paramNotInited[i] = false;
					} {
						sl.value = sl.spec.constrain(sl.value);
					};
				} {
					sl.visible = false;
				}
			};
		};
		var paramNotInited = true ! 3;

		prOutBus = Bus.control(Server.default, 1);
		filtTypes = Library.at(this.class.name, \filtTypes);
		freqFromInput = 0;

		filtType = 0;
		StaticText(window, Rect(10, 10, 60, 20)).string_("LFO type");
		filtMenu = PopUpMenu(window, Rect(80, 10, 90, 20))
		.items_(filtTypes.collect(_[0]))
		.action_({ |view|
			filtType = view.value;
			fixParamViews.defer;
			this.makeSynth;
		});

		startButton = Button(window, Rect(180, 10, 80, 20)).states_([["Start"],["Stop"]])
		.value_(0)
		.action_
		({|btn|
			if(btn.value == 1) {
				modControl.map(prOutBus);
				isMapped = true;
				synthInstance.set(\pollRate, pollRate/* * (watcher.notNil.binaryValue) */);
			} {
				modControl.unmap;
				isMapped = false;
				synthInstance.set(\pollRate, 0);
			};
		});

		paramComp = CompositeView(window, Rect(5, 40, 340, 90))
		.background_(Color(0.8, 1, 0.8));
		params = Array.fill(3, { |i|
			var sl = HrEZSlider(paramComp, Rect(5, 5 + (30 * i), 330, 20), "", nil, { |view|
				var argname = filtTypes[filtType][1][i * 2];
				if(argname.notNil) { synthInstance.set(argname, view.value) };
			});
			if(i == 2) { sl.visible = false };
			sl
		});

		spec = nil.asSpec;
		specEditor = HrSpecEditor(window, Rect(5, 135, 340, 50), 5@5, "range", 65)
		.value_(spec)
		.action_({ |view, paramName|
			spec = view.value;
			if(paramName == \warp) {
				this.makeSynth;
			} {
				synthInstance.set(paramName, spec.perform(paramName));
			};
		});
		specErrResp = SimpleController(specEditor).put(\message, { |obj, what, string, mood = 0|
			defer {
				parentApp.displayStatus(string, mood);
			};
		})
		.put(\viewDidClose, { specErrResp.remove });

		modControl = HadronModTargetControl.new(window, Rect(10, 190, 330, 20), parentApp, this);
		modControl.addDependant(this);

		pollRate = HrCtlMod.defaultPollRate;
		pollRateView = HrEZSlider(window, Rect(10, 215, 330, 20),
			"update rate", [1, 25], { |view|
				this.pollRate = view.value;
			}, pollRate, labelWidth: 100, numberWidth: 45
		);

		watcher = OSCresponderNode(Server.default.addr, '/modValue', { |time, resp, msg|
			if(msg[2] == replyID) {
				modControl.updateMappedGui(msg[3]);
			}
		}).add;

		saveGets = [
			{ filtType },
			{ spec },
			{ modControl.getSaveValues; },
			{ startButton.value; },
			{ pollRate },
			{ params.collect(_.value) }
		];

		saveSets = [
			{ |argg|
				filtType = argg;
				defer { filtMenu.value = argg; fixParamViews.value };
			},
			{ |argg|
				spec = argg;
				defer { specEditor.value = spec };
				this.makeSynth;
			},
			{ |argg| modControl.putSaveValues(argg); },
			{ |argg| startButton.valueAction_(argg); },
			{ |argg|
				if(argg.notNil) {
					this.pollRate = argg;
					pollRateView.tryPerform(\value_, argg);
				}
			},
			{ |argg| params.do({ |sl, i| sl.valueAction = argg[i] }) }
		];

		modGets = ();
		params.do { |sl, i| modGets.put(("param" ++ i).asSymbol, { sl.value }) };
		modGets.putAll((
			outmin: { spec.minval },
			outmax: { spec.maxval },
			outstep: { spec.step }
		));

		modSets = ();
		params.do { |sl, i| modSets.put(("param" ++ i).asSymbol, { |argg|
			var argname = filtTypes[filtType][1][i * 2];
			if(argname.notNil) { synthInstance.set(argname, argg) };
			defer { sl.value = argg };
		}) };
		modSets.putAll((
			outmin: { |argg|
				spec.minval = argg;
				synthInstance.set(\minval, argg);
				defer { specEditor.value = spec };
			},
			outmax: { |argg|
				spec.maxval = argg;
				synthInstance.set(\maxval, argg);
				defer { specEditor.value = spec };
			},
			outstep: { |argg|
				spec.step = argg;
				synthInstance.set(\step, argg);
				defer { specEditor.value = spec };
			}
		));

		modMapSets = ();
		params.do { |sl, i| modMapSets.put(("param" ++ i).asSymbol, { |argg| defer { sl.value = argg } }) };
		modSets.putAll((
			outmin: { |argg|
				spec.minval = argg;
				defer { specEditor.value = spec };
			},
			outmax: { |argg|
				spec.maxval = argg;
				defer { specEditor.value = spec };
			},
			outstep: { |argg|
				spec.step = argg;
				defer { specEditor.value = spec };
			}
		));

		fixParamViews.value;
		this.makeSynth;
	}

	synthArgs {
		var parmValues = Array(6);
		filtTypes[filtType][1].pairsDo { |name, spec, i|
			parmValues.add(name).add(params[i div: 2].value);
		};
		^[inBus0: inBusses[0], outBus0: outBusses[0], prOutBus: prOutBus,
			freqFromInput: freqFromInput,
			pollRate: pollRate * isMapped.binaryValue/* * (watcher.notNil.binaryValue) */,
			minval: spec.minval, maxval: spec.maxval, step: spec.step, 
		] ++ parmValues ++ this.getMapModArgs
	}

	makeSynthDef {
		replyID = UniqueID.next;
		SynthDef("HrLFO" ++ uniqueID, { |inBus0, outBus0, prOutBus, freqFromInput = 0,
			minval = 0, maxval = 1, step = 0, pollRate = 0|
			var parmValues = Array(3),
			sig = InFeedback.ar(inBus0, 1),
			localSpec = spec.copy
			.minval_(minval)  // replace hardcoded endpoints with control inputs
			.maxval_(maxval)
			.step_(step);
			filtTypes[filtType][1].pairsDo { |name, spec|
				parmValues.add(NamedControl(name, spec.asSpec.default, lags: 0.05, fixedLag: true))
			};
			// switch for ar/kr freq input
			parmValues[0] = Select.kr(freqFromInput, [parmValues[0], sig]);
			sig = filtTypes[filtType][0].asClass.kr(*parmValues);
			sig = localSpec.map(sig.range(0, 1));
			SendReply.kr(Impulse.kr(pollRate), '/modValue', sig, replyID);
			Out.kr(prOutBus, sig);
			Out.ar(outBus0, K2A.ar(sig));
		}).add;
	}

	updateBusConnections {
		synthInstance.set(\inBus0, inBusses[0], \outBus0, outBusses[0]);
	}

	setInputConnection { |index, connectionArray|
		super.setInputConnection(index, connectionArray);
		freqFromInput = inConnections[0][0].notNil.binaryValue;
		synthInstance.set(\freqFromInput, freqFromInput);
		defer { params[0].enabled = (freqFromInput == 0) };
	}

	cleanUp {
		modControl.removeDependant(this).remove;
		watcher.remove;
		prOutBus.free;
	}

	notifyPlugKill { |argPlug|
		if(inConnections[0].tryPerform(\at, 0) === argPlug) {
			this.setInputConnection(0, [nil, nil]);
		};
		modControl.plugRemoved(argPlug);
	}
	
	notifyPlugAdd { |argPlug|
		modControl.plugAdded;
	}

	updateModTargets { modControl.getParams }
	
	wakeFromLoad {
		modControl.doWakeFromLoad;
	}

	update { |obj, what, argument, oldplug, oldparam|
		if(#[currentSelPlugin, currentSelParam].includes(what)) {
			if(argument.notNil) {
				modControl.unmap(oldplug, oldparam);
				isMapped = modControl.map(prOutBus);
				synthInstance.set(\pollRate,
					pollRate * isMapped.binaryValue/* * (watcher.notNil.binaryValue) */
				);
				defer { startButton.value = isMapped.binaryValue };
			} {
				modControl.unmap(oldplug, oldparam);
				synthInstance.set(\pollRate, 0);
				isMapped = false;
				defer { startButton.value = 0 };
			};
		};
	}

	pollRate_ { |rate|
		pollRate = rate;
		if(synthInstance.notNil) {
			synthInstance.set(\pollRate, pollRate * isMapped.binaryValue/* * (watcher.notNil.binaryValue) */);
		};
		modControl.pollRate = rate;
	}
}

HrOscil : HadronPlugin {
	var oscilGuis, freqSl, ampSl, noiseFreqSl, noiseAmpSl, noiseRqSl, noisePanSl,
	ampEnv, envBtn, envGui, timescaleSl;

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|

		^super.new(argParentApp, "HrOscil", argIdent, argUniqueID, argExtraArgs, Rect(Window.screenBounds.width - 560.rand, Window.screenBounds.height - 375.rand, 560, 375), 0, 2, argCanvasXY).init
	}

	init {
		var noiseComp;

		if(Library.at('HrOscil', \sawbufs).isNil) {
			Library.put('HrOscil', \sawbufs, Library.at('HrOscil', \makeWavetables).value(
				8, Server.default, 2048
			));
			Library.put('HrOscil', \tribufs, Library.at('HrOscil', \makeWavetables).value(
				8, Server.default, 2048, spectrumFunc: { |topPartial|
					[(1, 3 .. topPartial).reciprocal.squared * #[1, -1], 0].lace(topPartial)
				}
			));
			NotificationCenter.register(Server.default, \didQuit, this.class, {
				// force wavetables to be rebuilt if server was stopped
				Library.removeEmptyAt('HrOscil', \sawbufs);
			})
		};

		ampEnv = Env([0, 1, 1, 0], [0.05, 0.9, 0.05], 0, 2);

		2.do { |i|
			StaticText(window, Rect(2 + (280*i), 2, 270, 20))
			.align_(\center).string_("Oscillator" + (i+1))
			.background_(Color(0.8 + (0.2 * i), 1, 0.8));
		};
		oscilGuis = Array.fill(2, { |i|
			var out;
			out = Library.at('HrOscil', \makeOscilGui).value(window,
				Rect(2 + (280*i), 22, 270, 226), this, i);
			out[\comp].background_(Color(0.8 + (0.2 * i), 1, 0.8));
			out
		});

		noiseComp = CompositeView(window, Rect(2, 254, 556, 68))
		.background_(Color(0.8, 0.8, 1));
		StaticText(noiseComp, Rect(2, 2, 552, 20))
		.align_(\center).string_("Noise oscillator");
		noiseFreqSl = HrEZSlider(noiseComp, Rect(2, 22, 270, 20),
			"detune", #[-12, 12, \lin, 0, 0], { |view|
				synthInstance.set(\noiseDetune, view.value);
			}
		);
		noiseRqSl = HrEZSlider(noiseComp, Rect(286, 22, 270, 20), "rq", \hadronrq, { |view|
			synthInstance.set(\noiseRq, view.value);
		}, 1);
		noiseAmpSl = HrEZSlider(noiseComp, Rect(2, 44, 270, 20),
			// 20.ampdb = 26.02 dB
			"amp", #[0, 80, \amp], { |view|
				synthInstance.set(\noiseAmp, view.value);
			}, 0
		);
		noisePanSl = HrEZSlider(noiseComp, Rect(286, 44, 270, 20), "pan", \bipolar, { |view|
			synthInstance.set(\noisePan, view.value);
		}, 0);

		freqSl = HrEZSlider(window, Rect(4, 328, 270, 20), "freq", \freq, { |view|
			synthInstance.set(\freq, view.value);
		}, 440);
		ampSl = HrEZSlider(window, Rect(4, 350, 270, 20), "amp", \amp, { |view|
			synthInstance.set(\amp, view.value);
		}, 0.1);

		timescaleSl = HrEZSlider(window, Rect(288, 350, 270, 20), "env time scale",
			#[0.01, 20, \exp], { |view|
				synthInstance.set(\timescale, view.value);
			}, 1
		);
		envBtn = Button(window, Rect(288, 328, 270, 20))
		.states_([
			["show amp envelope", Color.black, Color.gray(0.9)],
			["hide amp envelope", Color.black, Color(0.8, 1, 0.8)]
		])
		.action_({ |view|
			var win, gui;
			if(view.value > 0 and: { envGui.isNil }) {
				win = Window("amplitude envelope", Rect(
					Window.screenBounds.width - 405,
					max(0, Window.screenBounds.height - 350),
					400, 301
				))
				.onClose_({
					envGui = nil;
					view.value = 0;
				});
				gui = HrEnvelopeNodeEditor(win, Rect(2, 2, 396, 270))
				.env_(ampEnv)
				.action_({ |view|
					ampEnv = view.value;
					synthInstance.set(\env, ampEnv);
				})
				.insertAction_({ |view|
					ampEnv = view.value;
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
		
		saveGets = [
			{ ampSl.value },
			{ freqSl.value },
			{
				oscilGuis.collect { |env|
					env.use {
						[env[\coarseSl].value, env[\fineSl].value,
							env[\ampSl].value, env[\pwidthSl].value,
							env[\panSl].value, env[\waveType]
						]
					}
				}
			},
			{ noiseFreqSl.value },
			{ noiseAmpSl.value },
			{ noiseRqSl.value },
			{ noisePanSl.value },
			{ timescaleSl.value },
			{ ampEnv }
		];

		saveSets = [
			{ |argg| ampSl.valueAction_(argg) },
			{ |argg| freqSl.valueAction_(argg) },
			{ |argg|
				argg.do { |list, i|
					[#[\coarseSl, \fineSl, \ampSl, \pwidthSl, \panSl, \waveType],
						list]
					.flop.do { |row|
						var key, val;
						#key, val = row;
						if(key == \waveType) {
							oscilGuis[i].use {
								~waveType = val;
								~typeMenu.value = ~waveTypeNumbers[val];
								~pwidthSl.visible = (~waveType == \pulse);
							}
						} {
							oscilGuis[i][key].value = val;
						};
					};
				};
				this.makeSynth;
			},
			{ |argg| noiseFreqSl.valueAction = argg },
			{ |argg| noiseAmpSl.valueAction = argg },
			{ |argg| noiseRqSl.valueAction = argg },
			{ |argg| noisePanSl.valueAction = argg },
			{ |argg| timescaleSl.valueAction = argg },
			{ |argg| ampEnv = argg }
		];

		modGets = (
			freq: { freqSl.value },
			amp: { ampSl.value },
			timescale: { timescaleSl.value }
		);
		modSets = (
			freq: { |argg| synthInstance.set(\freq, argg); defer { freqSl.value = argg } },
			amp: { |argg| synthInstance.set(\amp, argg); defer { ampSl.value = argg } },
			noiseDetune: { |argg| synthInstance.set(\noiseDetune, argg); defer { noiseFreqSl.value = argg } },
			noiseAmp: { |argg| synthInstance.set(\noiseAmp, argg); defer { noiseAmpSl.value = argg } },
			noiseRq: { |argg| synthInstance.set(\noiseRq, argg); defer { noiseRqSl.value = argg } },
			noisePan: { |argg| synthInstance.set(\noisePan, argg); defer { noisePanSl.value = argg } },
			timescale: { |argg| synthInstance.set(\timescale, argg); defer { timescaleSl.value = argg } }
		);
		modMapSets = (
			freq: { |argg| defer { freqSl.value = argg } },
			amp: { |argg| defer { ampSl.value = argg } },
			noiseDetune: { |argg| defer { noiseFreqSl.value = argg } },
			noiseAmp: { |argg| defer { noiseAmpSl.value = argg } },
			noiseRq: { |argg| defer { noiseRqSl.value = argg } },
			noisePan: { |argg| defer { noisePanSl.value = argg } },
			timescale: { |argg| defer { timescaleSl.value = argg } }
		);
		2.do { |i|
			#[\coarse, \fine, \amp, \pwidth, \pan].do { |key|
				var modname = "o%_%".format(i, key).asSymbol,
				guiName = (key ++ "Sl").asSymbol;
				modGets.put(modname, { oscilGuis[i][guiName].value });
				modSets.put(modname, { |argg| synthInstance.set(modname, argg); defer { oscilGuis[i][guiName].value = argg } });
				modMapSets.put(modname, { |argg| defer { oscilGuis[i][guiName].value = argg } });
			}
		};

		this.makeSynth;
	}

	updateBusConnections { synthInstance.set(\outBus0, outBusses[0]) }

	synthArgs {
		^[
			freq: freqSl.value, amp: ampSl.value, outBus0: outBusses[0],
			noiseDetune: noiseFreqSl.value, noiseAmp: noiseAmpSl.value,
			noiseRq: noiseRqSl.value, noisePan: noisePanSl.value,
			env: ampEnv, timescale: timescaleSl.value
		]
		++ oscilGuis.collect { |env, i|
			[
				[i, ["coarse", "fine", "amp", "pwidth", "pan", "wavebuf"]]
				.flop.collect({ |row| "o%_%".format(*row).asSymbol }),
				[
					env[\coarseSl].value, env[\fineSl].value,
					env[\ampSl].value, env[\pwidthSl].value, env[\panSl].value,
					switch(env[\waveType])
					{ \sawtooth } { Library.at('HrOscil', \sawbufs)[0] }
					{ \pulse } { Library.at('HrOscil', \sawbufs)[0] }
					{ \triangle } { Library.at('HrOscil', \tribufs)[0] }
					// { \sine } { ... }
				]
			].flop
		}.flat ++ this.getMapModArgs
	}

	makeSynthDef {
		SynthDef("HrOscil" ++ uniqueID, { |freq = 440, amp = 0.1,
			o0_coarse = 0, o0_fine = 0, o0_amp = 1, o0_wavebuf, o0_pwidth, o0_pan,
			o1_coarse = 0, o1_fine = 0, o1_amp = 1, o1_wavebuf, o1_pwidth, o1_pan,
			noiseDetune = 0, noiseAmp = 0, noiseRq = 1, noisePan,
			outBus0, timescale = 1, gate = 1, doneAction = 2|

			var env = NamedControl.kr(\env, (0 ! 40).overWrite(Env.adsr.asArray)),
			eg = EnvGen.kr(env, gate, timeScale: timescale, doneAction: doneAction);

			// assuming 8 buffers
			var basefreq = 48.midicps,
			topfreq = basefreq * (2 ** 7),
			baselog = log2(basefreq),
			freqmap = ((log2(freq) - baselog) / (log2(topfreq) - baselog) * 7)
				.clip(0, 6.999);
			
			var oscs = [
				[o0_coarse, o0_fine, o0_amp, o0_wavebuf, o0_pwidth, o0_pan],
				[o1_coarse, o1_fine, o1_amp, o1_wavebuf, o1_pwidth, o1_pan]
			].collect({ |params, i|
				var localFreq, osc;
				localFreq = freq * midiratio(params[0] + (0.01 * params[1]));
				osc = VOsc.ar(params[3] + freqmap, localFreq, 0, params[2]);
				if(oscilGuis[i][\waveType] == \pulse) {
					osc = osc - DelayL.ar(osc, 0.08, params[4] / freq);
				};
				Pan2.ar(osc, params[5], amp)
			}).sum;
			oscs = oscs + Pan2.ar(BPF.ar(
				PinkNoise.ar(noiseAmp),
				freq * noiseDetune.midiratio, noiseRq
			), noisePan, amp * eg);
			Out.ar(outBus0, oscs);
		}, (0.05 ! 19).putEach(#[5, 11, 18], nil)).add;
	}

	cleanUp {
		if(envGui.notNil) {
			envGui[0].onClose_(nil).close
		};
	}

	*initClass {
		this.addHadronPlugin;
		StartUp.add {
			// need some shared resources but don't want too many classvars
			Library.put('HrOscil', \makeWavetables, { |numbufs, server, numFrames, lowMidi, spectrumFunc|
				numbufs = numbufs ? 8;
				server = server ? Server.default;
				numFrames = numFrames ? 2048;
				// default is sawtooth
				spectrumFunc = spectrumFunc ? { |numharm| (1..numharm).reciprocal };
				lowMidi = (lowMidi ? 48) / 12;
				
				Buffer.allocConsecutive(numbufs, server, numFrames, 1, { |buf, i|
					var	base = (i + lowMidi) * 12,
					numharm = (20000 / base.midicps).asInteger;
					buf.sine1Msg(spectrumFunc.(numharm));
				});
			});

			// common across all copies -- make once and share
			Library.put('HrOscil', \oscilGuiParent, Environment.make({
				~waveTypes = #[sawtooth, pulse, triangle/*, sine*/];
				~waveTypeNumbers = IdentityDictionary.new;
				~waveTypes.do { |type, i| ~waveTypeNumbers[type] = i };
			}));

			Library.put('HrOscil', \makeOscilGui, { |parent, bounds, inst, i = 0|
				var env = Environment(parent: Library.at('HrOscil', \oscilGuiParent))
				.make({
					~comp = CompositeView(parent, bounds);
					~waveType = \sawtooth;
					~typeMenu = PopUpMenu(~comp, Rect(2, 2, bounds.width - 4, 20))
					.items_(~waveTypes)
					.action_({ |view|
						env[\waveType] = env[\waveTypes][view.value];
						env[\pwidthSl].visible = (env[\waveType] == \pulse);
						inst.makeSynth;
					});

					~coarseSym = "o%_coarse".format(i).asSymbol;
					~fineSym = "o%_fine".format(i).asSymbol;
					~ampSym = "o%_amp".format(i).asSymbol;
					~panSym = "o%_pan".format(i).asSymbol;
					~pwidthSym = "o%_pwidth".format(i).asSymbol;
					~coarseSl = HrEZSlider(~comp, Rect(2, 24, 50, 200),
						"coarse", #[-12, 12, \lin, 1, 0], { |view|
							inst.synthInstance.set(env[\coarseSym], view.value);
						}, layout: \vert
					);
					~fineSl = HrEZSlider(~comp, Rect(54, 24, 50, 200),
						"fine", #[-100, 100, \lin, 0, 0], { |view|
							inst.synthInstance.set(env[\fineSym], view.value);
						}, layout: \vert
					);
					~ampSl = HrEZSlider(~comp, Rect(106, 24, 50, 200),
						"amp", #[0, 1, \amp, 0, 1], { |view|
							inst.synthInstance.set(env[\ampSym], view.value);
						}, layout: \vert
					);
					~panSl = HrEZSlider(~comp, Rect(158, 24, 50, 200),
						"pan", \bipolar, { |view|
							inst.synthInstance.set(env[\panSym], view.value);
						}, layout: \vert
					);
					~pwidthSl = HrEZSlider(~comp, Rect(210, 24, 50, 200),
						"width", #[0, 1, \lin, 0, 0.5], { |view|
							inst.synthInstance.set(env[\pwidthSym], view.value);
						}, layout: \vert
					).visible_(false);
				}).know_(true);
				env
			});
		};
	}

	hasGate { ^true }
	polySupport { ^true }
	defName { ^("HrOscil" ++ uniqueID) }
}