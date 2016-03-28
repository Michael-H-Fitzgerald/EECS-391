package edu.cwru.sepia.agent.planner.actions;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.planner.GameState;
import edu.cwru.sepia.agent.planner.Peasant;
import edu.cwru.sepia.agent.planner.Position;
import edu.cwru.sepia.util.Direction;

public class DepositAction implements StripsAction {
	int peasantId;
	Position peasantPos;
	Position townHallPos = GameState.TOWN_HALL_POSITION;
	boolean hasResource;
	
	public DepositAction(Peasant peasant){
		this.peasantId = peasant.getId();
		this.peasantPos = peasant.getPosition();
		this.hasResource = peasant.hasResource();
	}
	
	@Override
	public boolean preconditionsMet(GameState state) {
		return hasResource && peasantPos.isAdjacent(townHallPos);
	}

	@Override
	public GameState apply(GameState state) {
		state.applyDepositAction(this, peasantId);
		return state;
	}

	@Override
	public Action createSepiaAction() {
		Direction townhallDirection = peasantPos.getDirection(townHallPos);
		return Action.createPrimitiveDeposit(peasantId, townhallDirection);
	}
	
	@Override
	public int getPeasantId() {
		return peasantId;	
	}

}
