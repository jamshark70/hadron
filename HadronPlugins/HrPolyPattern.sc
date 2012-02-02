HrPolyPattern : HadronPlugin {
	var <key, <targetPlugin, iMadePdefs;
	var pdefMenu, newText, targetMenu, modsMenu;
	var subpatEdit;
	var plugList;
	var baseEvent;

	*initClass { this.addHadronPlugin }

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		^super.new(argParentApp, "HrPolyPattern", argIdent, argUniqueID, argExtraArgs, Rect((Window.screenBounds.width - 600).rand, (Window.screenBounds.width - 450).rand, 600, 450), 0, 2, argCanvasXY).init
	}

	init {
		var width = (window.bounds.width - 16) * 0.25;

		if(extraArgs.size >= 1) {
			key = extraArgs[0].asSymbol;
			if(HrPbindef.exists(key).not) {
				HrPbindef(key, \dur, 1);
				iMadePdefs = iMadePdefs.add(key);
			}
		};

		HrPbindef.addDependant(this);
		pdefMenu = PopUpMenu(window, Rect(2, 2, width, 20))
		.items_(["empty"] ++ HrPbindef.keys.asArray.sort)
		.value_(0)
		.action_({ |view|
			this.key = view.item.asSymbol;
		});

		newText = TextField(window, Rect(6 + width, 2, width))
		.string_("(name new)")
		.action_({ |view|
			var new = view.string.asSymbol;
			if(HrPbindef.exists(new).not) {
				HrPbindef(new, \dur, 1);
				iMadePdefs = iMadePdefs.add(new);
			};
			this.key = new;  // switch anyway
		});

		plugList = parentApp.alivePlugs.select(_.polySupport);

		targetMenu = PopUpMenu(window, Rect(10 + (width*2), 2, width, 20))
		.items_(["None"] ++ plugList.collect { |plug| plug.boundCanvasItem.string }.sort)
		.value_(0)
		.action_({ |view|
			this.targetPlugin = plugList[view.value-1];  // nil if "none"
		});

		modsMenu = PopUpMenu(window, Rect(14 + (width*3), 2, width, 20))
		.items_([])
		.action_({ |view|
			if(subpatEdit.focusedRow.notNil) {
				subpatEdit[subpatEdit.focusedRow].key = view.item.asSymbol;
			}
		});

		subpatEdit = HrPbindefEditor(window, Rect(2, 24,
			window.bounds.width - 4, window.bounds.height - 26));
		subpatEdit.addDependant(this);
		if(key.notNil) {
			this.key = key
		};
	}

	key_ { |newKey|
		var temp;
		if(HrPbindef.exists(newKey)) {
			key = newKey;
			defer {
				subpatEdit.key = key;
				// key may never be nil here, b/c of HrPbindef.exists check
				// remove 'empty' once a valid pattern has been assigned
				if(pdefMenu.items[0] == "empty") {
					temp = pdefMenu.items.copy;
					temp.removeAt(0);
					pdefMenu.items = temp;
				};
				pdefMenu.value = pdefMenu.items.detectIndex { |name| (name.asSymbol == key) } ? 0;
			};
			// playing stuff
		} {
			parentApp.displayStatus("% does not exist as a pattern. Use the text box at right to make a new one.".format(newKey), -1);
			defer {
				pdefMenu.value = pdefMenu.items.detectIndex { |name| (name.asSymbol == key) } ? 0;
			};
		}
	}

	targetPlugin_ { |plug|
		if((plug.tryPerform(\polySupport) ? false) or: { plug.isNil }) {
			targetPlugin.tryPerform(\polyMode_, false);
			targetPlugin = plug;
			if(plug.notNil) {
				targetPlugin.polyMode = true;
				baseEvent = (instrument: plug.defName.asSymbol /* others? */);
			};
			defer {
				modsMenu.items = ["None"] ++ if(plug.notNil) { targetPlugin.modSets.keys.asArray.sort } { [] };
				if(subpatEdit.focusedRow.notNil) {
					// this will set modsMenu's value
					this.update(subpatEdit, \focusedRow, subpatEdit.focusedRow);
				};
			}
		} {
			parentApp.displayStatus("Plugin % does not support polyphonic use.".format(plug.boundCanvasItem.string), -1);
		}
	}

	// for play: Pchain(..., Pfunc({ baseEvent }))

	// we don't know how many synths are here
	// setting the group touches all of them
	updateBusConnections { group.set(\outBus0, outBusses[0]) }
	cleanUp {
		HrPbindef.removeDependant(this);
		subpatEdit.removeDependant(this);
		iMadePdefs.do { |name| HrPbindef(name).remove };
	}

	notifyPlugAdd { |plug|
		var oldPlug, oldPlugList;
		if(plug.polySupport) {
			oldPlugList = plugList;
			plugList = parentApp.alivePlugs.select(_.polySupport);
			defer {
				oldPlug = oldPlugList[targetMenu.value - 1];
				targetMenu.items = ["None"] ++ plugList.collect { |plug|
					plug.boundCanvasItem.string
				}.sort;
				targetMenu.value = plugList.indexOf(oldPlug) ? 0;
			};
		}
	}

	notifyPlugKill { |plug|
		if(plug === targetPlugin) {
			this.targetPlugin = nil;
		};
		this.notifyPlugAdd(plug);
	}

	update { |obj, what ... more|
		var temp;
		[obj, what, more].debug("HrPolyPattern:update");
		if(obj === subpatEdit) {
			switch(what)
			// { \status } {
			// 	if(more[0] != \editing) {
			// 		focusedRow = nil
			// 	}
			// }
			{ \focusedRow } {
				if(more[0].notNil) {
					temp = subpatEdit[more[0]].key.asSymbol;
					modsMenu.value = modsMenu.items.detectIndex({ |item|
						item.asSymbol == temp
					}) ? 0;
				};
			}
			{ \addRow } {
				subpatEdit[more[0]].focus(true);
			}
			{ \deleteRow } {
				subpatEdit[min(more[0], subpatEdit.size-1)].tryPerform(\focus, true);
			}
		} {
			case
			{ #[added, removed].includes(what) } {
				defer {
					pdefMenu.items = (if(key.isNil) { ["empty"] } { [] })
						++ HrPbindef.keys.asArray.sort;
					pdefMenu.value = pdefMenu.items.detectIndex { |name| (name.asSymbol == key) } ? 0;
				}
			}
		}
	}
}