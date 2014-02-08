package net.mathdoku.plus.storage;

import net.mathdoku.plus.grid.CellChange;
import net.mathdoku.plus.grid.GridCell;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import robolectric.RobolectricGradleTestRunner;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
public class CellChangeStorageTest {
	private CellChangeStorage mCellChangeStorage = new CellChangeStorage();
	private String mLine;
	private List<GridCell> mArrayListOfGridCellsStub = mock(ArrayList.class);
	private int mRevisionNumber = 596;


	private GridCell createGridCellMock(int id) {
		GridCell gridCell = mock(GridCell.class);
		when(gridCell.getCellId()).thenReturn(id);

		return gridCell;
	}

	@Test(expected = NullPointerException.class)
	public void fromStorageString_NullLine_False() throws Exception {
		mLine = null;
		assertThat(mCellChangeStorage.fromStorageString(mLine,
				mArrayListOfGridCellsStub, mRevisionNumber), is(false));
	}

	@Test
	public void fromStorageString_RevisionIdToLow_False() throws Exception {
		mLine = "CELL_CHANGE:[0:1::]";
		mRevisionNumber = 368;
		assertThat(mCellChangeStorage.fromStorageString(mLine,
				mArrayListOfGridCellsStub, mRevisionNumber), is(false));
	}

	@Test
	public void fromStorageString_SingleCellChangeForCellContainingAUserValue_GridCellCreated() {
		mLine = "CELL_CHANGE:[0:1::]";
		assertThat(mCellChangeStorage.fromStorageString(mLine,
				mArrayListOfGridCellsStub, mRevisionNumber), is(true));
	}

	@Test
	public void fromStorageString_SingleCellChangeForCellContainingOneMaybeValue_GridCellCreated() {
		mLine = "CELL_CHANGE:[0:0:1:]";
		assertThat(mCellChangeStorage.fromStorageString(mLine,
				mArrayListOfGridCellsStub, mRevisionNumber), is(true));
	}

	@Test
	public void fromStorageString_SingleCellChangeForCellContainingTwoMaybeValues_GridCellCreated() {
		mLine = "CELL_CHANGE:[0:0:1,2,:]";
		assertThat(mCellChangeStorage.fromStorageString(mLine,
				mArrayListOfGridCellsStub, mRevisionNumber), is(true));
	}

	@Test
	public void fromStorageString_SingleCellChangeForCellContainingManyMaybeValues_SuccessfulRead() {
		mLine = "CELL_CHANGE:[0:0:1,2,3,4,5,6,7,8,9,:]";
		assertThat(mCellChangeStorage.fromStorageString(mLine,
				mArrayListOfGridCellsStub, mRevisionNumber), is(true));
	}

	@Test
	public void fromStorageString_NestedCellChange2_SuccessfulRead() {
		mLine = "CELL_CHANGE:[1:1::[0:0:3,:],]";
		assertThat(mCellChangeStorage.fromStorageString(mLine,
				mArrayListOfGridCellsStub, mRevisionNumber), is(true));
	}

	@Test
	public void fromStorageString_NestedCellChange3_SuccessfulRead() {
		mLine = "CELL_CHANGE:[4:1::[2:0:3,:],[16:0:2,3,4,:],]";
		assertThat(mCellChangeStorage.fromStorageString(mLine,
				mArrayListOfGridCellsStub, mRevisionNumber), is(true));
	}

	@Test
	public void fromStorageString_CellChangeInvalidStorageStringLabel_FailedToRead() {
		mLine = "WRONG:[0:1::]";
		assertThat(mCellChangeStorage.fromStorageString(mLine,
				mArrayListOfGridCellsStub, mRevisionNumber), is(false));
	}

	@Test
	public void fromStorageString_CellChangeInvalidStorageStringUnbalancedBrackets_FailedToRead() {
		mLine = "CELL_CHANGE:[0:1::";
		assertThat(mCellChangeStorage.fromStorageString(mLine,
				mArrayListOfGridCellsStub, mRevisionNumber), is(false));
	}

	@Test
	public void fromStorageString_CellChangeInvalidStorageStringTooManyArguments_FailedToRead() {
		mLine = "CELL_CHANGE:[0:1:::]";
		assertThat(mCellChangeStorage.fromStorageString(mLine,
				mArrayListOfGridCellsStub, mRevisionNumber), is(false));
	}

	@Test
	public void fromStorageString_CellChangeRevisionTooLow_FailedToRead() {
		mLine = "CELL_CHANGE:[0:1::]";
		mRevisionNumber = 368;
		assertThat(mCellChangeStorage.fromStorageString(mLine,
				mArrayListOfGridCellsStub, mRevisionNumber), is(false));
	}

	@Test
	public void toStorageString_CellChangeWithUserValue_StorageStringCreated() {
		int cellId = 5;
		int cellUserValue = 2;
		List<Integer> cellPossibles = new ArrayList<Integer>();

		CellChange cellChange = new CellChange(createGridCellMock(cellId), cellUserValue,
						cellPossibles);
		assertThat(mCellChangeStorage.toStorageString(cellChange),
				is("CELL_CHANGE:[5:2::]"));
	}

	@Test
	public void toStorageString_CellChangeWithOneMaybeValue_StorageStringCreated() {
		int cellId = 5;
		int cellUserValue = 0; // Cell has no user value
		List<Integer> cellPossibles = new ArrayList<Integer>();
		cellPossibles.add(3);

		CellChange cellChange = new CellChange(createGridCellMock(cellId), cellUserValue,
											   cellPossibles);
		assertThat(mCellChangeStorage.toStorageString(cellChange),
				is("CELL_CHANGE:[5:0:3,:]"));
	}

	@Test
	public void toStorageString_CellChangeWithMultipleMaybeValue_StorageStringCreated() {
		int cellId = 5;
		int cellUserValue = 0; // Cell has no user value
		List<Integer> cellPossibles = new ArrayList<Integer>();
		cellPossibles.add(3);
		cellPossibles.add(4);
		cellPossibles.add(5);

		CellChange cellChange = new CellChange(createGridCellMock(cellId), cellUserValue,
											   cellPossibles);
		assertThat(mCellChangeStorage.toStorageString(cellChange),
				is("CELL_CHANGE:[5:0:3,4,5,:]"));
	}

	@Test
	public void toStorageString_CellChangeWithOneRelatedCellChange_StorageStringCreated() {
		int rootCellId = 4;
		int rootCellUserValue = 1;
		List<Integer> rootCellPossibles = new ArrayList<Integer>();
		CellChange cellChange = new CellChange(createGridCellMock(rootCellId), rootCellUserValue,
												rootCellPossibles);

		int relatedCellId = 2;
		int relatedCellUserValue = 0; // No user value
		List<Integer> relatedCellPossibles = new ArrayList<Integer>();
		relatedCellPossibles.add(3);
		CellChange relatedCellChange = new CellChange(createGridCellMock(relatedCellId),
						relatedCellUserValue, relatedCellPossibles);
		cellChange.addRelatedMove(relatedCellChange);

		assertThat(mCellChangeStorage.toStorageString(cellChange),
				is("CELL_CHANGE:[4:1::[2:0:3,:],]"));
	}

	@Test
	public void toStorageString_CellChangeWithMultipleRelatedCellChanges_StorageStringCreated() {
		int rootCellId = 4;
		int rootCellUserValue = 1;
		List<Integer> rootCellPossibles = new ArrayList<Integer>();
		CellChange cellChange = new CellChange(createGridCellMock(rootCellId), rootCellUserValue,
											   rootCellPossibles);

		int relatedCellId1 = 2;
		int relatedCellUserValue1 = 0; // No user value
		List<Integer> relatedCellPossibles1 = new ArrayList<Integer>();
		relatedCellPossibles1.add(3);
		CellChange relatedCellChange1 = new CellChange(createGridCellMock(relatedCellId1),
													  relatedCellUserValue1, relatedCellPossibles1);
		cellChange.addRelatedMove(relatedCellChange1);

		int relatedCellId2 = 16;
		int relatedCellUserValue2 = 0; // No user value
		List<Integer> relatedCellPossibles2 = new ArrayList<Integer>();
		relatedCellPossibles2.add(2);
		relatedCellPossibles2.add(3);
		relatedCellPossibles2.add(4);
		CellChange relatedCellChange2 = new CellChange(createGridCellMock(relatedCellId2),
													  relatedCellUserValue2, relatedCellPossibles2);
		cellChange.addRelatedMove(relatedCellChange2);

		assertThat(mCellChangeStorage.toStorageString(cellChange),
				   is("CELL_CHANGE:[4:1::[2:0:3,:],[16:0:2,3,4,:],]"));
	}
}
