HrListSelector : SCViewHolder {
	var availView, activeView, <availItems, <activeItems, <allItems;
	var buttons;
	var <>action;

	*new { |parent, bounds| ^super.new.init(parent, bounds) }

	init { |parent, bounds|
		var halfwidth = bounds.width * 0.5,
		height = bounds.height - 4,
		btnHeight = 16, btnWidth = 24,
		gap = 4,
		top = (height - (btnHeight * 4) - (gap * 3)) * 0.5,
		tempBounds;

		availItems = [];
		activeItems = [];
		allItems = [];

		this.view = CompositeView(parent, bounds);

		availView = ListView(view, Rect(2, 2, halfwidth - 25, height));
		activeView = ListView(view, Rect(halfwidth + 15, 2, halfwidth - 25, height));

		buttons = Array(4);

		buttons.add(
			Button(view, tempBounds = Rect(
				halfwidth - 8, availView.bounds.top + top, btnHeight, btnHeight
			))
			.states_([[">>"]])
			.action_({
				if(availItems.size > 0) {
					this.makeItemsActive((0 .. availItems.size-1));
				};
			})
		);
		buttons.add(
			Button(view, tempBounds = tempBounds.moveBy(0, btnHeight + gap))
			.states_([[">"]])
			.action_({
				this.makeItemsActive(availView.value ? -1);
			});
		);
		buttons.add(
			Button(view, tempBounds = tempBounds.moveBy(0, btnHeight + gap))
			.states_([["<"]])
			.action_({
				this.makeItemsInactive(activeView.value ? -1);
			});
		);
		buttons.add(
			Button(view, tempBounds = tempBounds.moveBy(0, btnHeight + gap))
			.states_([["<<"]])
			.action_({
				if(activeItems.size > 0) {
					this.makeItemsInactive((0 .. activeItems.size-1));
				};
			});
		);

		this.refresh;
	}

	// after this:
	// allItems is the new array
	// activeItems is whatever was active, provided it's in the new allItems array
	// availItems is whatever exists in the new array that is not active
	allItems_ { |items|
		allItems = items;
		activeItems = activeItems.select { |item|
			this.includesEqual(allItems, item)
		};
		availItems = allItems.reject { |item|
			this.includesEqual(activeItems, item)
		};
		this.refresh;
	}

	// after this:
	// allItems is the union of new availItems + activeItems
	//// ** This may make items disappear from allItems
	//// But this is doing just what you asked: replacing availItems
	// activeItems is unchanged
	// availItems is the new array
	availItems_ { |items|
		availItems = items.copy;
		allItems = availItems.union(activeItems);
		this.refresh
	}

	// after this:
	// allItems is the union of new availItems + new array
	// activeItems is the new array
	// availItems is unchanged
	activeItems_ { |items|
		activeItems = items.copy;
		allItems = availItems.union(activeItems);
		this.refresh
	}

	// this is a way to replace the whole state with just one gui update
	// more efficient than the other
	setAllAndActiveItems { |all, active|
		allItems = all;
		activeItems = active.copy;
		availItems = all.reject { |item| this.includesEqual(active, item) };
		this.refresh;
	}

	refresh {
		var temp = availView.value;
		availView.items_(availItems).value_(temp);
		temp = activeView.value;
		activeView.items_(activeItems).value_(temp);

		2.do { |i| buttons[i].enabled = (availItems.size > 0) };
		(2..3).do { |i| buttons[i].enabled = (activeItems.size > 0) };
	}

	makeItemsActive { |indices|
		indices = indices.asArray;
		if(indices.every(_.isMemberOf(Integer))) {
			indices.do { |i|
				// ignore out-of-range
				if(i.inclusivelyBetween(0, availItems.size - 1)) {
					activeItems = activeItems.add(availItems[i]);
				};
			};
			availItems = availItems.reject { |item, i| indices.includes(i) };
			this.refresh.doAction;
		} {
			Error("HrListSelector:makeItemsActive - indices should all be Integers").throw
		}
	}

	makeItemsInactive { |indices|
		indices = indices.asArray;
		if(indices.every(_.isMemberOf(Integer))) {
			indices.do { |i|
				if(i.inclusivelyBetween(0, activeItems.size - 1)) {
					availItems = availItems.add(activeItems[i]);
				};
			};
			activeItems = activeItems.reject { |item, i| indices.includes(i) };
			this.refresh.doAction;
		} {
			Error("HrListSelector:makeItemsInactive - indices should all be Integers").throw
		}
	}

	// SCViewHolder action is for the view, not (this)
	doAction { action.value(this) }

	// slightly hacky: 3.4 doesn't have includesEqual in common
	// so I have to have a substitute (in 'this')
	includesEqual { |collection, item|
		^collection.detect(_ == item).notNil
	}
}


+ Collection {
	unionEqual { |that|
		var result = this.copy;
		that.do { |item|
			if(result.detect(_ == item).isNil) {
				result = result.add(item);
			}
		};
		^result
	}
}