package edu.cwru.sepia.agent.planner.actions;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.planner.GameState;
import edu.cwru.sepia.agent.planner.Position;

public class MoveAction implements StripsAction {
	int peasantId;
	Position destination;

	public MoveAction(int peasantId, Position destination){
		this.peasantId = peasantId;
		this.destination = destination;
	}

	@Override
	public boolean preconditionsMet(GameState state) {
		Position current = state.getPeasantPosition(peasantId);
		return current != destination && !state.isOccupied(destination);
	}

	@Override
	public GameState apply(GameState state) {
		state.movePosition(this, peasantId, destination);
		return state;
	}

	@Override
	public Action createSepiaAction() {
		return Action.createCompoundMove(peasantId, destination.x, destination.y);
	}

	@Override
	public int getPeasantId() {
		return peasantId;	
	}

}
