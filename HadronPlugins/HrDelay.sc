HrDelay : HadronPlugin {
	var maxDelayTime, buffers;

	// these depend on button values - the arrays allow synchronous access
	var feedbacks, delays;
	var ffreqSl, mixSl, feedbackSl, delaySl, mulLSl, mulRSl,
	syncFBButton, syncDTButton, fbSynced = false, dtSynced = false,
	secBeatsButton;

	*initClass {
		var dict, i, ctl, ctls;
		this.addHadronPlugin;
		StartUp.add {
			Library.put(this, \modIndices, (
				L_feedback: 6, R_feedback: 7,
				L_delay: 8, R_delay: 9,
				L_to_L: 10, L_to_R: 11,
				R_to_L: 12, R_to_R: 13
			));

			ServerBoot.add {
				SynthDef("HrDelay", { |inBus0, outBus0, ffreq = 8000, mix = 0.5,
					bufs = #[0, 1],
					feedback = #[0, 0], delaytime = #[0.1, 0.1],
					mulL = #[1, 0], mulR = #[0, 1]|

					var input = In.ar(inBus0, 2),
					fbsig = LocalIn.ar(2) * feedback,
					sig = input + fbsig,
					delay;

					sig = [mulL, mulR].collect { |mul, i| mul * sig[i] }.sum;

					delay = BufDelayL.ar(bufs, sig, delaytime - ControlDur.ir);
					LocalOut.ar(LPF.ar(delay, ffreq));

					delay = XFade2.ar(input, delay, mix.madd(2, -1));
					Out.ar(outBus0, delay);
				}, #[nil, nil, 0.05, 0.05, nil, nil, 0.05, 0.05, 0.05, 0.05]).add;
			};
		};
	}

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		^super.new( argParentApp, "HrDelay", argIdent, argUniqueID, argExtraArgs,
			Rect((Window.screenBounds.width - 540).rand, (Window.screenBounds.width - 138).rand,
				540, 138),
			2, 2, argCanvasXY
		).init
	}

	init {
		var comps;
		if(extraArgs.size >= 1) {
			maxDelayTime = extraArgs[0].asFloat;
		} {
			maxDelayTime = 2;
		};
		// nextPowerOfTwo: see BufDelayN/L/C help
		buffers = Buffer.allocConsecutive(2, Server.default,
			(maxDelayTime * Server.default.sampleRate).nextPowerOfTwo, 1);

		feedbacks = 0 ! 2;
		delays = min(1, maxDelayTime) ! 2;

		feedbackSl = Array(2);
		delaySl = Array(2);
		mulLSl = Array(2);
		mulRSl = Array(2);

		comps = #["Left", "Right"].collect { |str, i|
			var color = Color(0.8 + (0.2*i), 0.8, 1.0);
			StaticText(window, Rect(84 + (228*i), 2, 226, 22)).align_(\center)
			.background_(color)
			.string_(str + "input");
			CompositeView(window, Rect(84 + (228*i), 24, 226, 90))
			.background_(color);
		};

		["Feedback", "To left", "To right"].do { |str, i|
			StaticText(window, Rect(2, 48 + (22*i), 80, 20)).string_(str);
		};

		2.do { |i|
			delaySl.add(HrEZSlider(comps[i], Rect(2 + (42*i), 2, 180, 20),
				"", [0, maxDelayTime], { |view|
					delays[i] = view.value;
					if(i == 0 and: { syncDTButton.value > 0 }) {
						delays[1] = view.value;
					};
					synthInstance.set(\delaytime, delays);
				},
				min(1, maxDelayTime),
				labelWidth: 0
			));

			feedbackSl.add(HrEZSlider(comps[i], Rect(2 + (42*i), 24, 180, 20),
				"", #[0, 1], { |view|
					feedbacks[i] = view.value;
					if(i == 0 and: { syncFBButton.value > 0 }) {
						feedbacks[1] = view.value;
					};
					synthInstance.set(\feedback, feedbacks);
				},
				0,
				labelWidth: 0
			));

			mulLSl.add(HrEZSlider(comps[i], Rect(2, 46, 222, 20),
				"", #[0, 1], { |view|
					synthInstance.set(\mulL, mulLSl.collect(_.value));
				},
				1-i,
				labelWidth: 0
			));

			mulRSl.add(HrEZSlider(comps[i], Rect(2, 68, 222, 20),
				"", #[0, 1], { |view|
					synthInstance.set(\mulR, mulRSl.collect(_.value));
				},
				i,
				labelWidth: 0
			));
		};

		secBeatsButton = Button(window, Rect(2, 26, 80, 20))
		.states_([["seconds"], ["beats"]])
		.action_({ |view| 0
		});

		syncDTButton = Button(window, Rect(268, delaySl[0].bounds.top + comps[0].bounds.top, 86, 20))
		.states_([["dual"], ["synced"]])
		.action_({ |view|
			var tempDelays;
			dtSynced = (view.value == 0);  // inside here
			delaySl[1].visible = dtSynced;
			delays = delaySl[[0, dtSynced.binaryValue]].collect(_.value);
			tempDelays = delays.copy;
			dtSynced = dtSynced.not;  // for the outside world
			if(mappedMods['L_delay'].notNil) {
				tempDelays[0] = mappedMods['L_delay'].asMap;
				if(dtSynced.not and: { mappedMods['L_delay'] == mappedMods['R_delay'] }) {
					this.mapModCtl('R_delay', -1);
				};
			};
			if(mappedMods['R_delay'].notNil) {
				tempDelays[1] = mappedMods['R_delay'].asMap;
			} {
				// mappedMods['R_delay'].isNil here
				if(dtSynced and: { mappedMods['L_delay'].notNil }) {
					this.mapModCtl('R_delay', mappedMods['L_delay']);
					tempDelays = tempDelays[0];
				};
			};
			if(synthInstance.notNil) {
				synthInstance.set(\delaytime, tempDelays);
			};
		});

		syncFBButton = Button(window, Rect(268, feedbackSl[0].bounds.top + comps[0].bounds.top, 86, 20))
		.states_([["dual"], ["synced"]])
		.action_({ |view|
			var tempFeedbacks;
			fbSynced = (view.value == 0);  // inside here
			feedbackSl[1].visible = fbSynced;
			feedbacks = feedbackSl[[0, fbSynced.binaryValue]].collect(_.value);
			tempFeedbacks = feedbacks.copy;
			fbSynced = fbSynced.not;  // for the outside world
			if(mappedMods['L_feedback'].notNil) {
				tempFeedbacks[0] = mappedMods['L_feedback'].asMap;
				if(fbSynced.not and: { mappedMods['L_feedback'] == mappedMods['R_feedback'] }) {
					this.mapModCtl('R_feedback', -1);
				};
			};
			if(mappedMods['R_feedback'].notNil) {
				tempFeedbacks[1] = mappedMods['R_feedback'].asMap;
			} {
				// mappedMods['R_feedback'].isNil here
				if(fbSynced and: { mappedMods['L_feedback'].notNil }) {
					this.mapModCtl('R_feedback', mappedMods['L_feedback']);
					tempFeedbacks = tempFeedbacks[0];
				};
			};
			if(synthInstance.notNil) {
				synthInstance.set(\feedback, tempFeedbacks);
			};
		});

		mixSl = HrEZSlider(window, Rect(2, 116, 268, 20), "Mix", nil, { |view|
			synthInstance.set(\mix, view.value);
		}, 0.5);
	
		ffreqSl = HrEZSlider(window, Rect(272, 116, 268, 20), "Filter freq", \freq, { |view|
			synthInstance.set(\ffreq, view.value);
		}, 8000);

		saveGets = [
			{ ffreqSl.value },
			{ mixSl.value },
			{ [mulLSl.collect(_.value), mulRSl.collect(_.value)] },
			{ [secBeatsButton, syncFBButton, syncDTButton].collect(_.value) },
			{ [delays, feedbacks] }
		];
		saveSets = [
			{ |argg| ffreqSl.valueAction_(argg) },
			{ |argg| mixSl.valueAction_(argg) },
			{ |argg| 
				[mulLSl, mulRSl].do { |sliders, i|
					sliders.do { |sl, j|
						sl.valueAction_(argg[i][j])
					}
				}
			},
			{ |argg|
				[secBeatsButton, syncFBButton, syncDTButton].do { |bt, i|
					bt.valueAction_(argg[i])
				}
			},
			{ |argg|
				#delays, feedbacks = argg;
				delaySl.do { |sl, i| sl.valueAction_(delays[i]) };
				feedbackSl.do { |sl, i| sl.valueAction_(feedbacks[i]) };
			}
		];


		modGets.putAll((
			mix: { mixSl.value },
			ffreq: { ffreqSl.value }
		));
		modSets.putAll((
			mix: { |argg| defer { mixSl.valueAction = argg } },
			ffreq: { |argg| defer { ffreqSl.valueAction = argg } }
		));
		modMapSets.putAll((
			mix: { |argg| defer { mixSl.value = argg } },
			ffreq: { |argg| defer { ffreqSl.value = argg } }
		));

		[feedback: feedbackSl, delay: delaySl, to_L: mulLSl, to_R: mulRSl]
		.pairsDo { |key, sliders|
			[#['L', 'R'], sliders].flop.do { |ctlpair|
				var slider = ctlpair[1],
				modname = "%_%".format(ctlpair[0], key).asSymbol;
				modGets.put(modname, { slider.value });
				modMapSets.put(modname, { |argg| defer { slider.value = argg } });
				modSets.put(modname, { |argg| defer { slider.valueAction = argg } });
			};
		};

		// defer makeSynth until buffers ready - will this work?
		fork {
			Server.default.sync;
			this.makeSynth;
		}
	}

	synthArgs {
		^[inBus0: inBusses[0], outBus0: outBusses[0], bufs: buffers,
			ffreq: ffreqSl.value, mix: mixSl.value,
			feedback: feedbacks, delaytime: delays,
			mulL: mulLSl.collect(_.value), mulR: mulRSl.collect(_.value)
		]
	}
	makeSynthDef {}
	defName { ^this.class.name }
	updateBusConnections {
		synthInstance.set(*this.synthArgs)
	}

	cleanUp {
		buffers.do(_.free);
	}

	mapModCtl { |paramName, ctlBus|
		var checkSync = { |checkName, bool|
			if(bool and: { paramName == checkName }) {
				this.mapModCtl(paramName.asString.put(0, $R).asSymbol, ctlBus);
			};
		},
		modIndex = Library.at(this.class, \modIndices).at(paramName) ?? { paramName },
		node;
		checkSync.('L_delay', dtSynced);
		checkSync.('L_feedback', fbSynced);
		if(ctlBus == -1 or: { ctlBus.tryPerform(\index) == -1 }) {
			mappedMods.removeAt(paramName.asSymbol);
		} {
			mappedMods.put(paramName.asSymbol, ctlBus);
		};
		if((node = this.tryPerform(\synthInstance)).notNil) {
			node.map(modIndex, ctlBus);
		};
	}
}
