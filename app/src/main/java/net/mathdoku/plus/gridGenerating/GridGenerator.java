package net.mathdoku.plus.gridGenerating;

import android.os.AsyncTask;
import android.util.Log;

import com.srlee.DLX.MathDokuDLX;

import net.mathdoku.plus.Preferences;
import net.mathdoku.plus.config.Config;
import net.mathdoku.plus.config.Config.AppMode;
import net.mathdoku.plus.developmentHelper.DevelopmentHelper;
import net.mathdoku.plus.enums.CageOperator;
import net.mathdoku.plus.enums.PuzzleComplexity;
import net.mathdoku.plus.grid.CageBuilder;
import net.mathdoku.plus.grid.Grid;
import net.mathdoku.plus.grid.GridBuilder;
import net.mathdoku.plus.grid.GridCage;
import net.mathdoku.plus.grid.GridCell;
import net.mathdoku.plus.gridDefinition.GridDefinition;
import net.mathdoku.plus.painter.Painter;
import net.mathdoku.plus.storage.database.DatabaseHelper;
import net.mathdoku.plus.storage.database.GridDatabaseAdapter;
import net.mathdoku.plus.util.Util;

import java.util.ArrayList;
import java.util.Random;

/**
 * An asynchronous task that generates a grid.
 */
public class GridGenerator extends AsyncTask<Void, String, Void> {
	private static final String TAG = "MathDoku.GridGenerator";

	// Remove "&& false" in following line to show debug information about
	// creating cages when running in development mode.
	@SuppressWarnings("PointlessBooleanExpression")
	static final boolean DEBUG_GRID_GENERATOR = (Config.mAppMode == AppMode.DEVELOPMENT) && false;
	@SuppressWarnings("PointlessBooleanExpression")
	public static final boolean DEBUG_GRID_GENERATOR_FULL = DEBUG_GRID_GENERATOR && false;

	// The parameters use to generate a grid
	private final GridGeneratingParameters mGridGeneratingParameters;

	// The complexity of a puzzle will be determined by following factors.
	private int mMaxCagePermutations;
	private int mMaximumSingleCellCages;

	// The user that will use the generated grid.
	final Listener mListener;

	// Random generator
	private Random mRandom;

	// Size of the grid
	private int mGridSize;

	// Cell and solution
	private ArrayList<GridCell> mCells;
	private int[][] mSolutionMatrix;

	// Cages
	private CageTypeGenerator mGridCageTypeGenerator;
	private ArrayList<GridCage> mCages;
	private int[][] mCageMatrix;

	// Grid
	private Grid mGrid;

	// Additional option for generating the grid
	GridGeneratorOptions mGridGeneratorOptions;

	boolean mForceExceptionInDevelopmentModeDueToSlowGenerating = false;

	// Timestamp for logging purposes
	private long mTimeStarted;
	private long mTimeStartedSolution;

	// The grid generator options are used in development mode only to generate
	// fake games.
	public class GridGeneratorOptions {
		// The number of games to be generated.
		public int numberOfGamesToGenerate;

		// Whether a dummy game will be generated (true), rather than a regular
		// game (false). A dummy game might have more than one solution (and is
		// therefore not playable).
		public boolean createFakeUserGameFiles;

		// Grid size and setting for hiding/displaying the operators will be
		// randomly chosen when set to true. If false than all generated games
		// will have the specified grid size and operator setting.
		public boolean randomGridSize;
		public boolean randomHideOperators;
	}

	// The user that will use the grid once this task finished generating it.
	public interface Listener {
		/**
		 * Informs the listener(s) when grid generating has successfully
		 * finished. The created grid is passed as parameter.
		 * 
		 * @param grid
		 *            The newly generated grid.
		 */
		public void onFinishGridGenerating(Grid grid);

		/**
		 * Inform(s) the listener about cancellation of grid generating.
		 */
		public void onCancelGridGenerating();
	}

	/**
	 * Creates a new instance of {@link GridGenerator}. Though the signature of
	 * the constructor suggests otherwise, the singleton classes
	 * {@link DatabaseHelper}, {@link Painter}, {@link Preferences} and
	 * {@link Util} all have to be initialised before this generator can be
	 * used.
	 * 
	 * @param gridSize
	 *            The size of the grid to be created.
	 * @param hideOperators
	 *            True in case should be solvable without using operators.
	 * @param puzzleComplexity
	 *            The complexity of the grid to be created.
	 * @param packageVersionNumber
	 *            The version number of the app used to generate the grid.
	 * @param listener
	 *            The user (either the Main UI or the Development Tools menu)
	 *            who will receive the callback as soon as the grid is
	 *            generated.
	 */
	GridGenerator(int gridSize, boolean hideOperators,
			PuzzleComplexity puzzleComplexity, int packageVersionNumber,
			Listener listener) {
		mGridSize = gridSize;

		// Set all grid generating parameters. GameSeed will be set later.
		mGridGeneratingParameters = new GridGeneratingParameters();
		mGridGeneratingParameters.mGameSeed = (new Random()).nextLong();
		mGridGeneratingParameters.mGeneratorRevisionNumber = packageVersionNumber;
		if (puzzleComplexity == PuzzleComplexity.RANDOM) {
			// Determine a random complexity to be used for this specific
			// puzzle. Note that the random complexity itself has to be
			// excluded.
			int index = new Random()
					.nextInt(PuzzleComplexity.values().length - 1) + 1;
			mGridGeneratingParameters.mPuzzleComplexity = PuzzleComplexity
					.values()[index];
		} else {
			mGridGeneratingParameters.mPuzzleComplexity = puzzleComplexity;
		}
		mGridGeneratingParameters.mHideOperators = hideOperators;

		mMaximumSingleCellCages = mGridSize / 2;
		switch (mGridGeneratingParameters.mPuzzleComplexity) {
		case RANDOM:
			// This value has already been transformed to another complexity
			// value but is required by the compiler.
			if (Config.mAppMode == AppMode.DEVELOPMENT) {
				throw new RuntimeException(
						"GridGenerator: complexity RANDOM was not transformed.");
			}
			break;
		case VERY_EASY:
			mGridGeneratingParameters.mMaxCageSize = 2;

			// Overwrite the maximum number of single cell cages which are
			// allowed for the smaller grid size. This setting is needed to
			// avoid long generation times because cages do not fit.
			mMaximumSingleCellCages = Math.min(4, mMaximumSingleCellCages);

			mGridGeneratingParameters.mMaxCageResult = 99; /*
															 * Not used
															 * effectively as
															 * the maximum will
															 * be9 * 8 = 72
															 */
			mMaxCagePermutations = 20;
			break;
		case EASY:
			mGridGeneratingParameters.mMaxCageSize = 3;
			mGridGeneratingParameters.mMaxCageResult = 999; /*
															 * Not used
															 * effectively as
															 * the maximum will
															 * be 9 * 8 = 648
															 */
			mMaxCagePermutations = 20;
			break;
		case NORMAL:
			mGridGeneratingParameters.mMaxCageSize = 4;
			mGridGeneratingParameters.mMaxCageResult = 2500; /*
															 * Real maximum = 9
															 * 9 * 8 * 8 = 5,184
															 */
			mMaxCagePermutations = 40;
			break;
		case DIFFICULT:
			mGridGeneratingParameters.mMaxCageSize = 5;
			mGridGeneratingParameters.mMaxCageResult = 9999; /*
															 * Real maximum = 9
															 * 9 * 9 * 8 * 8 =
															 * 46,656
															 */
			mMaxCagePermutations = 80;
			break;
		case VERY_DIFFICULT:
			mGridGeneratingParameters.mMaxCageSize = 6;
			mGridGeneratingParameters.mMaxCageResult = 99999; /*
															 * Real maximum = 9
															 * * 9 * 9 * 8 8 * 8
															 * = 373,248
															 */
			mMaxCagePermutations = 120;
			break;
		}

		if (DEBUG_GRID_GENERATOR) {
			Log.i(TAG, "Game seed: " + mGridGeneratingParameters.mGameSeed);
		}

		mListener = listener;

		setGridGeneratorOptions(null);
	}

	/**
	 * Sets the additional options for the grid generator. Only to be used in
	 * development mode.
	 * 
	 * @param gridGeneratorOptions
	 *            The additional options to be set.
	 */
	void setGridGeneratorOptions(GridGeneratorOptions gridGeneratorOptions) {
		if (gridGeneratorOptions == null) {
			// Use default values if options are not specified
			this.mGridGeneratorOptions = new GridGeneratorOptions();
			this.mGridGeneratorOptions.numberOfGamesToGenerate = 1;
			this.mGridGeneratorOptions.createFakeUserGameFiles = false;
			this.mGridGeneratorOptions.randomGridSize = false;
			this.mGridGeneratorOptions.randomHideOperators = false;
			return;
		}

		// Use specified options only if running in development mode.
		if (Config.mAppMode == AppMode.DEVELOPMENT) {
			this.mGridGeneratorOptions = gridGeneratorOptions;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.os.AsyncTask#doInBackground(Params[])
	 */
	@Override
	protected Void doInBackground(Void... params) {
		if (mGridSize < 3) {
			return null;
		}

		mTimeStarted = System.currentTimeMillis();

		boolean hasUniqueSolution = false;
		int num_attempts = 0;

		// Use the game seed to initialize the randomize which is used to
		// generate the game. Overwrite this game seed with the fixed value of a
		// given game in case you want to recreate the same grid. All you need
		// to ensure is that you the correct revision of this GridGenerator
		// module. Please be aware that in case the implementation of the random
		// method changes, it will not be possible to recreate the grids!
		mRandom = new Random(mGridGeneratingParameters.mGameSeed);

		do {
			// Check whether the generating process should be aborted due to
			// cancellation of the grid dialog.
			if (isCancelled()) {
				return null;
			}

			num_attempts++;

			mTimeStartedSolution = System.currentTimeMillis();

			handleNewAttemptStarted(num_attempts);

			mCells = new ArrayList<GridCell>();
			int cellNumber = 0;
			for (int i = 0; i < mGridSize * mGridSize; i++) {
				mCells.add(new GridCell(cellNumber++, mGridSize));
			}

			randomiseGrid();

			// Check whether the generating process should be aborted due to
			// cancellation of the grid dialog.
			if (isCancelled()) {
				return null;
			}

			// Create the cages.
			this.mCages = new ArrayList<GridCage>();
			if (createCages(mGridGeneratingParameters.mHideOperators) == false
					&& isCancelled() == false) {
				// For some reason the creation of the cages was not successful.
				// Start over again.
				continue;
			}

			// Check whether the generating process should be aborted due to
			// cancellation of the grid dialog.
			if (isCancelled()) {
				return null;
			}

			if (Config.mAppMode == AppMode.DEVELOPMENT) {
				if (mGridGeneratorOptions.createFakeUserGameFiles) {
					// The faked user games files do not require a unique
					// solution which results in much faster generation time.

					// Create a temporary grid object which is used to store the fake game.
					GridBuilder mGridBuilder = new GridBuilder();
					mGridBuilder
							.setGridSize(mGridSize)
							.setCells(mCells)
							.setCages(mCages)
							.setGridGeneratingParameters(
									mGridGeneratingParameters);
					Grid grid = mGridBuilder.build();
					if (grid == null) {
						Log.e(TAG, "Can not create grid.");
						continue;
					}
					grid.save();

					publishProgress(
							DevelopmentHelper.GRID_GENERATOR_PROGRESS_UPDATE_MESSAGE,
							"");
					publishProgress(DevelopmentHelper.GRID_GENERATOR_PROGRESS_UPDATE_PROGRESS);

					// Check if more puzzles have to generated.
					if (num_attempts < mGridGeneratorOptions.numberOfGamesToGenerate) {
						// Determine random size and hide operator values of
						// next grid
						mGridGeneratingParameters.mGameSeed = (new Random())
								.nextLong();
						mRandom = new Random(
								mGridGeneratingParameters.mGameSeed);
						if (mGridGeneratorOptions.randomGridSize) {
							mGridSize = 4 + (new Random().nextInt(6));
						}
						if (mGridGeneratorOptions.randomHideOperators) {
							mGridGeneratingParameters.mHideOperators = new Random()
									.nextBoolean();
						}

						// Fake a non unique solution so another grid is
						// generated.
						hasUniqueSolution = false;
						continue;
					} else {
						return null;
					}
				}
			}

			if (DEBUG_GRID_GENERATOR) {
				publishProgress(
						DevelopmentHelper.GRID_GENERATOR_PROGRESS_UPDATE_MESSAGE,
						"Verify unique solution");
			}

			// Create the grid object
			GridBuilder mGridBuilder = new GridBuilder();
			mGridBuilder
					.setGridSize(mGridSize)
					.setCells(mCells)
					.setCages(mCages)
					.setGridGeneratingParameters(mGridGeneratingParameters);
			Grid grid = mGridBuilder.build();

			// Determine whether grid has a unique solution.
			hasUniqueSolution = new MathDokuDLX(mGridSize, grid.mCages)
					.hasUniqueSolution();

			if (DEBUG_GRID_GENERATOR) {
				if (hasUniqueSolution) {
					Log.d(TAG, "This grid has a unique solution.");
				} else {
					Log.d(TAG, "This grid does not have a unique solution.");
				}
			}

			if (Config.mAppMode == AppMode.DEVELOPMENT) {
				// Sometimes grid generation takes too long. Until I have a game
				// seed which reproduces this problem I can not fix it. If such
				// a game is found in development, an exception will be thrown
				// to investigate it.
				if (System.currentTimeMillis() - mTimeStarted > 30 * 1000) {
					Log
							.i(TAG,
									"Game generation takes too long ("
											+ (int) ((System
													.currentTimeMillis() - mTimeStarted) / 1000)
											+ " secs). "
											+ "Please investigate. Grid generating params are:");
					Log
							.i(TAG,
									" - mGeneratorRevisionNumber: "
											+ mGridGeneratingParameters.mGeneratorRevisionNumber);
					Log.i(TAG, " - mPuzzleComplexity: "
							+ mGridGeneratingParameters.mPuzzleComplexity);
					Log.i(TAG, " - mGameSeed: "
							+ mGridGeneratingParameters.mGameSeed);
					Log.i(TAG, " - mMaxCageSize: "
							+ mGridGeneratingParameters.mMaxCageSize);
					Log.i(TAG, " - mMaxCageResult: "
							+ mGridGeneratingParameters.mMaxCageResult);
					Log.i(TAG, " - mHideOperators: "
							+ mGridGeneratingParameters.mHideOperators);
					publishProgress(
							DevelopmentHelper.GRID_GENERATOR_PROGRESS_UPDATE_MESSAGE,
							"Slow game generation. See LogCat for grid generating parameters which might help to reproduce the problem. Game seed = "
									+ mGridGeneratingParameters.mGameSeed);
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					} // Pause a moment to publish message
					mForceExceptionInDevelopmentModeDueToSlowGenerating = true;
					return null;
				}
			}

			if (hasUniqueSolution) {
				mGrid = grid;
			}
		} while (hasUniqueSolution == false);
		if (DEBUG_GRID_GENERATOR) {
			Log.d(TAG, "Found puzzle with unique solution in " + num_attempts
					+ " attempts.");
		}
		return null;
	}

	/**
	 * Handles the generator starting a new attempt. Should be overridden in
	 * subclass in case special handling is needed.
	 * 
	 * @param attemptCount
	 *            The number of the attempt already completed plus 1.
	 */
	void handleNewAttemptStarted(int attemptCount) {
	}

	@Override
	protected void onProgressUpdate(String... values) {
		if (DEBUG_GRID_GENERATOR) {
			long timeElapsed = System.currentTimeMillis() - mTimeStarted;
			if (values.length == 1 && values[0] != null) {
				if (values[0]
						.equals(DevelopmentHelper.GRID_GENERATOR_PROGRESS_UPDATE_SOLUTION)) {
					Log
							.i(TAG,
									Long.toString(timeElapsed)
											+ ": found a solution for this puzzle in "
											+ (System.currentTimeMillis() - mTimeStartedSolution)
											+ " " + "milliseconds");
					mTimeStartedSolution = System.currentTimeMillis();
				}
			}
			if (values.length >= 2 && values[0] != null && values[1] != null
					&& !values[1].equals("")) {
				Log.i(TAG, Long.toString(timeElapsed) + ": " + values[1]);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
	 */
	@Override
	protected void onPostExecute(Void result) {
		if (Config.mAppMode == AppMode.DEVELOPMENT) {
			if (mForceExceptionInDevelopmentModeDueToSlowGenerating) {
				throw new RuntimeException(
						"Investigate slow game generation. See logcat above for more info.");
			}
		}

		// Save the create grid object
		if (mGrid != null) {
			mGrid.save();
			mListener.onFinishGridGenerating(mGrid);
		}
	}

	/*
	 * Fills the grid with random numbers, per the rules:
	 * 
	 * - 1 to <row size> on every row and column - No duplicates in any row or
	 * column.
	 */
	private void randomiseGrid() {
		int attempts;
		mSolutionMatrix = new int[this.mGridSize][this.mGridSize];
		for (int value = 1; value < this.mGridSize + 1; value++) {
			for (int row = 0; row < this.mGridSize; row++) {
				attempts = 20;
				GridCell cell;
				int column;
				while (true) {
					column = this.mRandom.nextInt(this.mGridSize);
					cell = getCellAt(row, column);
					if (--attempts == 0)
						break;
					if (cell.getCorrectValue() != 0)
						continue;
					if (valueInColumn(column, value))
						continue;
					break;
				}
				if (attempts == 0) {
					this.clearValue(value--);
					break;
				}
				cell.setCorrectValue(value);
				mSolutionMatrix[row][column] = value;
			}
		}
	}

	/**
	 * Creates cages for the current grid which is already filled with numbers.
	 * 
	 * @param hideOperators
	 *            True in case cages should hide the operator. False in
	 *            operators should be visible.
	 * @return True in case the cages have been created successfully.
	 */
	private boolean createCages(boolean hideOperators) {
		this.mGridCageTypeGenerator = CageTypeGenerator.getInstance();

		boolean restart;
		int attempts = 0;
		do {
			restart = false;
			attempts++;
			mCageMatrix = new int[this.mGridSize][this.mGridSize];
			for (int row = 0; row < this.mGridSize; row++) {
				for (int col = 0; col < this.mGridSize; col++) {
					mCageMatrix[row][col] = -1;
				}
			}

			if (mGridGeneratingParameters.mMaxCageSize >= CageTypeGenerator.MAX_CAGE_SIZE) {
				// Drop a first (bigger) cage type somewhere in the grid.
				int remainingAttemptsToPlaceBigCageType = 10;
				while (remainingAttemptsToPlaceBigCageType > 0) {
					// Check whether the generating process should be aborted
					// due to cancellation of the grid dialog.
					if (isCancelled()) {
						return false;
					}

					GridCageType gridCageType = mGridCageTypeGenerator
							.getRandomCageType(
									mGridGeneratingParameters.mMaxCageSize,
									mGridSize, mGridSize, mRandom);
					if (gridCageType != null) {
						// Determine a random row and column at which the mask
						// will be placed. Use +1 in calls to randomizer to
						// prevent exceptions in case the entire height and/or
						// width is needed for the cage type.
						int startRow = mRandom
								.nextInt((mGridSize - gridCageType.getHeight()) + 1);
						int startCol = mRandom
								.nextInt((mGridSize - gridCageType.getWidth()) + 1);

						// Determine the origin cell of the cage type in case
						// the cage type mask is put at the randomly determined
						// position.
						int[] coordinatesTopLeft = gridCageType
								.getOriginCoordinates(startRow, startCol);

						// Get coordinates for the cage cells and add the cage.
						// Note: no checking is done on the maximum permutations
						// for the first cage.
						int[][] cageTypeCoordinates = gridCageType
								.getCellCoordinates(getCellAt(
										coordinatesTopLeft[0],
										coordinatesTopLeft[1]));
						GridCage firstCage = createCage(cageTypeCoordinates,
								4 * mMaxCagePermutations);
						if (firstCage != null) {
							mCages.add(firstCage);
							break;
						}
					}

					// Try another time to drop a big cage type unless maximum
					// number of tries has been reached.
					remainingAttemptsToPlaceBigCageType--;
				}
			}

			// Fill remainder of grid
			int countSingles = 0;
			for (GridCell cell : this.mCells) {
				if (cell.cellInAnyCage()) {
					continue; // Cell already in a cage, skip
				}

				// Determine a random cage which will start at this cell.
				GridCage cage = selectRandomCageType(cell);

				if (cage.getNumberOfCells() == 1) {
					countSingles++;
					if (countSingles > mMaximumSingleCellCages) {
						if (DEBUG_GRID_GENERATOR) {
							// Too many singles
							publishProgress(
									DevelopmentHelper.GRID_GENERATOR_PROGRESS_UPDATE_MESSAGE,
									"Found more single cell cages than allowed ("
											+ mMaximumSingleCellCages
											+ ") in attempt " + attempts);
						}
						clearAllCages();
						restart = true;
						break;
					}
				}

				// Add the cage to the grid
				this.mCages.add(cage);
			}

			// A valid grid has been created. Check if grid was never created
			// before.
			if (!restart) {
				String gridDefinition = GridDefinition.getDefinition(mCells,
						mCages, mGridGeneratingParameters);
				if (new GridDatabaseAdapter()
						.getByGridDefinition(gridDefinition) != null) {
					// The exact same grid has been created before. Create
					// another grid.
					clearAllCages();
					restart = true;
					if (DEBUG_GRID_GENERATOR) {
						publishProgress(
								DevelopmentHelper.GRID_GENERATOR_PROGRESS_UPDATE_MESSAGE,
								"Grid has been generated before " + attempts);
					}
					continue;
				}
			}

			// If not succeeded in 20 attempts then stop and try all over again.
			if (attempts >= 20) {
				return false;
			}
		} while (restart);

		if (DEBUG_GRID_GENERATOR) {
			printCageCreationDebugInformation(null);
		}

		return true;
	}

	/**
	 * Create a new cage which originates at the given cell. The cage type for
	 * this cage will be randomly determined.
	 * 
	 * @param origin
	 *            The cell at which the cage originates.
	 * @return The selected grid cage type.
	 */
	private GridCage selectRandomCageType(GridCell origin) {
		if (DEBUG_GRID_GENERATOR_FULL) {
			Log.i(TAG, "Determine valid cages for cell[" + origin.getRow()
					+ "," + origin.getColumn() + "]");
		}

		// Store indexes of all defined cages types, except cage type 0 which is
		// a single cell, in a temporary list of available cages.
		ArrayList<Integer> availableCages = new ArrayList<Integer>();
		for (int i = 1; i < mGridCageTypeGenerator
				.size(mGridGeneratingParameters.mMaxCageSize); i++) {
			availableCages.add(i);
		}

		GridCageType selectedGridCageType;
		do {
			// Check whether the generating process should be aborted
			// due to cancellation of the grid dialog.
			if (isCancelled()) {
				return null;
			}

			// Randomly select any cage from the list of available cages. As
			// soon as a cage type is selected, it is removed from the list of
			// available cage types so it will not be selected again.
			int randomIndex = this.mRandom.nextInt(availableCages.size());
			int cageTypeToBeChecked = availableCages.get(randomIndex);
			availableCages.remove(randomIndex);
			selectedGridCageType = mGridCageTypeGenerator
					.getCageType(cageTypeToBeChecked);

			// Get coordinates of all cells involved when this cage type is
			// placed at this origin.
			int[][] cageTypeCoordinates = selectedGridCageType
					.getCellCoordinates(origin);

			// Build mask for this cage
			boolean[][] maskNewCage = new boolean[this.mGridSize][this.mGridSize];
			int[] maskNewCageRowCount = new int[this.mGridSize];
			int[] maskNewCageColCount = new int[this.mGridSize];
			boolean cageIsValid = true;
			for (int[] cageTypeCoordinate : cageTypeCoordinates) {
				int row = cageTypeCoordinate[0];
				int col = cageTypeCoordinate[1];

				if (row < 0 || row >= this.mGridSize || col < 0
						|| col >= this.mGridSize) {
					// Coordinates of this cell in cage falls outside the
					// grid.
					cageIsValid = false;
					break;
				} else if (mCageMatrix[row][col] >= 0) {
					// Cell is already used in another cage
					cageIsValid = false;
					break;
				} else {
					// Cell can be used for this new cage.
					maskNewCage[row][col] = true;
					maskNewCageRowCount[row]++;
					maskNewCageColCount[col]++;
				}
			}
			if (!cageIsValid) {
				continue;
			}

			if (DEBUG_GRID_GENERATOR_FULL) {
				// Print solution, cage matrix and maskNewCage
				printCageCreationDebugInformation(maskNewCage);
			}

			// Check next cage in case an overlapping subset is found in the
			// columns
			if (hasOverlappingSubsetOfValuesInColumns(maskNewCage,
					maskNewCageColCount)) {
				continue;
			}

			// Check next cage in case an overlapping subset is found in the
			// rows
			if (hasOverlappingSubsetOfValuesInRows(maskNewCage,
					maskNewCageRowCount)) {
				continue;
			}

			// So far the cage is still valid
			GridCage cage = createCage(cageTypeCoordinates,
					mMaxCagePermutations);
			if (cage != null) {
				// As we randomly check available cages, we can stop as soon as
				// a valid cage is found which does fit on this position.
				return cage;
			}

			// No cage created due to too many permutations. Check next cage.
		} while (availableCages.size() > 0);

		// No cage, other than a single cell, does fit on this position in the
		// grid.
		if (DEBUG_GRID_GENERATOR_FULL) {
			// Print solution, cage matrix and maskNewCage
			boolean[][] maskNewCage = new boolean[this.mGridSize][this.mGridSize];
			maskNewCage[origin.getRow()][origin.getColumn()] = true;
			printCageCreationDebugInformation(maskNewCage);
		}

		// Create the new cage for a single cell.
		return createCage(mGridCageTypeGenerator
				.getSingleCellCageType()
				.getCellCoordinates(origin), 0);
	}

	/**
	 * Create the cage at the given coordinates.
	 * 
	 * @param cageTypeCoordinates
	 *            The coordinates to be used for the cage.
	 * @param maxPermutations
	 *            The maximum permutations allowed to create the cage. Use 0 in
	 *            case no checking on the number of permutations needs to be
	 *            done.
	 * @return The grid cage which is created. Null in case the cage has too
	 *         many permutations.
	 */
	private GridCage createCage(int[][] cageTypeCoordinates, int maxPermutations) {
		CageBuilder cageBuilder = new CageBuilder();
		cageBuilder.setHideOperator(mGridGeneratingParameters.mHideOperators);
		int newCageId = mCages.size();
		cageBuilder.setId(newCageId);

		int cellsInCage[] = new int[cageTypeCoordinates.length];
		int cellCorrectValues[] = new int[cageTypeCoordinates.length];
		ArrayList<GridCell> cellsInCageArrayList = new ArrayList<GridCell>();
		int index = 0;
		for (int[] cageTypeCoordinate : cageTypeCoordinates) {
			int row = cageTypeCoordinate[0];
			int col = cageTypeCoordinate[1];
			GridCell gridCell = getCellAt(row, col);
			cellsInCage[index] = gridCell.getCellId();
			cellsInCageArrayList.add(gridCell);
			cellCorrectValues[index] = gridCell.getCorrectValue();
			index++;
		}

		cageBuilder.setCells(cellsInCage);
		CageOperator cageOperator = generateCageOperator(cellCorrectValues);
		cageBuilder.setCageOperator(cageOperator);
		cageBuilder.setResult(getCageResult(cellCorrectValues, cageOperator));
		if (cellsInCage.length == 1) {
			// The cage operator for a single cell is set to *not* hidden regardless of the grid generating parameters. This prevents that for a single cage cell the operator can be revealed using the context menu.
			cageBuilder.setHideOperator(false);
		} else {
			cageBuilder.setHideOperator(mGridGeneratingParameters.mHideOperators);
		}

		// All data is gathered which is needed to build the cage.
		GridCage cage = cageBuilder.build();

		// Finally check whether the number of permutations of possible solutions for the cage is not to big.
		ComboGenerator comboGenerator = new ComboGenerator(mGridSize);
		ArrayList<int[]> possibleCombos = comboGenerator
				.getPossibleCombos(cage, cellsInCageArrayList);
		if (maxPermutations > 0 && possibleCombos.size() > maxPermutations) {
			// This cage has too many permutations which fulfill the
			// cage requirements. As this reduces the chance to find a
			// solution for the puzzle too much, the cage type will not
			// returned.
			if (DEBUG_GRID_GENERATOR_FULL) {
				Log.i(TAG, "This cage type has been rejected as it has more "
						+ "than " + maxPermutations + " initial "
						+ "permutations which fulfill the cage requirement.");
			}
			return null;
		}
		cage.setPossibleCombos(possibleCombos);

		// Set cage id in all cells used by this cage.
		for (GridCell gridCell : cellsInCageArrayList) {
			gridCell.setCageId(newCageId);
		}

		// Update the cage matrix
		for (int[] cageTypeCoordinate : cageTypeCoordinates) {
			int row = cageTypeCoordinate[0];
			int col = cageTypeCoordinate[1];
			mCageMatrix[row][col] = newCageId;
		}

		return cage;
	}

	/**
	 * Print debug information for create cage process to logging.
	 * 
	 * @param maskNewCage
	 *            Mask of cage type which is currently processed.
	 */
	private void printCageCreationDebugInformation(boolean[][] maskNewCage) {
		Log.i(TAG, "   Checking cage type");
		String cageIdFormat = "%d";
		String emptyCell = ".";
		String usedCell = "X";
		if (this.mCages.size() > 100) {
			cageIdFormat = "%03d";
			emptyCell = "  .";
			usedCell = "  X";
		} else if (this.mCages.size() > 10) {
			cageIdFormat = "%02d";
			emptyCell = " .";
			usedCell = " X";
		}
		for (int row = 0; row < this.mGridSize; row++) {
			String line = "      ";
			for (int col = 0; col < this.mGridSize; col++) {
				line += " " + mSolutionMatrix[row][col];
			}
			line += "   ";
			for (int col = 0; col < this.mGridSize; col++) {
				line += " "
						+ (mCageMatrix[row][col] == -1 ? emptyCell : String
								.format(cageIdFormat, mCageMatrix[row][col]));
			}
			if (maskNewCage != null) {
				line += "   ";
				for (int col = 0; col < this.mGridSize; col++) {
					line += " "
							+ (maskNewCage[row][col] ? usedCell : emptyCell);
				}
			}
			Log.i(TAG, line);
		}
	}

	/**
	 * Determines whether the given new cage contains a subset of values in its
	 * columns which is also used in the columns of another cage.
	 * 
	 * @param maskNewCage
	 *            A mask of the new cage. Cells which are in use by this cage
	 *            have value 1. Cells not used have a value -1.
	 * @param maskNewCageColCount
	 *            The number of rows per column in use by this new cage.
	 * @return True in case an overlapping subset of values is found. False
	 *         otherwise.
	 */
	private boolean hasOverlappingSubsetOfValuesInColumns(
			boolean[][] maskNewCage, int[] maskNewCageColCount) {
		for (int newCageCol = 0; newCageCol < this.mGridSize; newCageCol++) {
			if (maskNewCageColCount[newCageCol] > 1) {
				// This column in the new cage has more than one row and
				// therefore needs to be checked with columns of other cages.

				// Compare the column in which the new cage is placed with cages
				// in other columns of the grid.
				for (int col = 0; col < this.mGridSize; col++) {
					if (col != newCageCol) {

						// Cages which are already checked during processing of
						// this column of the new cage, can be skipped.
						ArrayList<Integer> cagesChecked = new ArrayList<Integer>();

						// Iterate all cells in the column from top to bottom.
						for (int row = 0; row < this.mGridSize; row++) {
							int otherCageId = mCageMatrix[row][col];
							if (otherCageId >= 0
									&& maskNewCage[row][newCageCol]
									&& !cagesChecked.contains(otherCageId)) {
								// Cell[row][col] is used in a cage which is not
								// yet checked. This is the first row for which
								// the new cage and the other cage has a cell in
								// the columns which are compared.
								cagesChecked.add(otherCageId);

								// Check all remaining rows if the checked
								// columns contain a cell for the new cage and
								// the other cage.
								int[] valuesUsed = new int[this.mGridSize];
								for (int row2 = row; row2 < this.mGridSize; row2++) {
									if (mCageMatrix[row2][col] == otherCageId
											&& maskNewCage[row2][newCageCol]) {
										// Both cages contain a cell on the same
										// row. Remember values used in those
										// cells.
										valuesUsed[mSolutionMatrix[row2][col] - 1]++;
										valuesUsed[mSolutionMatrix[row2][newCageCol] - 1]++;
									}
								}

								// Determine which values are used in both cages
								ArrayList<Integer> duplicateValues = new ArrayList<Integer>();
								for (int i = 0; i < this.mGridSize; i++) {
									if (valuesUsed[i] > 1) {
										// Value (i+1) has been used in both
										// columns of the new cage and the other
										// cage.
										duplicateValues.add(i + 1);
									}
								}
								if (duplicateValues.size() > 1) {
									// At least two values have been found which
									// are used in both columns of the new cage
									// and the other cage. As this would result
									// in a non-unique solution, the cage is not
									// valid.
									if (DEBUG_GRID_GENERATOR_FULL) {
										Log
												.i(TAG,
														"         This cage type will result in a "
																+ "non-unique solution. The new cage "
																+ "contains values "
																+ duplicateValues
																		.toString()
																+ " in column "
																+ newCageCol
																+ " which are also used in "
																+ "column "
																+ col
																+ " within cage "
																+ otherCageId
																+ ".");
									}
									return true;
								}
							}
						}
					}
				}
			}
		}

		// No overlapping subset found
		return false;
	}

	/**
	 * Determines whether the given new cage contains a subset of values in its
	 * rows which is also used in the rows of another cage.
	 * 
	 * @param maskNewCage
	 *            A mask of the new cage. Cells which are in use by this cage
	 *            have value 1. Cells not used have a value -1.
	 * @param maskNewCageRowCount
	 *            The number of columns per row in use by this new cage.
	 * @return True in case an overlapping subset of values is found. False
	 *         otherwise.
	 */
	private boolean hasOverlappingSubsetOfValuesInRows(boolean[][] maskNewCage,
			int[] maskNewCageRowCount) {
		for (int newCageRow = 0; newCageRow < this.mGridSize; newCageRow++) {
			if (maskNewCageRowCount[newCageRow] > 1) {
				// This row in the new cage has more than one column and
				// therefore needs to be checked with rows of other cages.

				// Compare the row in which the new cage is placed with cages
				// in other rows of the grid.
				for (int row = 0; row < this.mGridSize; row++) {
					if (row != newCageRow) {

						// Cages which are already checked during processing of
						// this row of the new cage, can be skipped.
						ArrayList<Integer> cagesChecked = new ArrayList<Integer>();

						// Iterate all cells in the row from left to right.
						for (int col = 0; col < this.mGridSize; col++) {
							int otherCageId = mCageMatrix[row][col];
							if (otherCageId >= 0
									&& maskNewCage[newCageRow][col]
									&& !cagesChecked.contains(otherCageId)) {
								// Cell[row][col] is used in a cage which is not
								// yet checked. This is the first column for
								// which
								// the new cage and the other cage has a cell in
								// the rows which are compared.
								cagesChecked.add(otherCageId);

								// Check all remaining columns if the checked
								// rows contain a cell for the new cage and
								// the other cage.
								int[] valuesUsed = new int[this.mGridSize];
								for (int cols2 = col; cols2 < this.mGridSize; cols2++) {
									if (mCageMatrix[row][cols2] == otherCageId
											&& maskNewCage[newCageRow][cols2]) {
										// Both cages contain a cell on the same
										// columns. Remember values used in
										// those
										// cells.
										valuesUsed[mSolutionMatrix[row][cols2] - 1]++;
										valuesUsed[mSolutionMatrix[newCageRow][cols2] - 1]++;
									}
								}

								// Determine which values are used in both cages
								ArrayList<Integer> duplicateValues = new ArrayList<Integer>();
								for (int i = 0; i < this.mGridSize; i++) {
									if (valuesUsed[i] > 1) {
										// Value (i+1) has been used in both
										// columns of the new cage and the other
										// cage.
										duplicateValues.add(i + 1);
									}
								}
								if (duplicateValues.size() > 1) {
									// At least two values have been found which
									// are used in both rows of the new cage
									// and the other cage. As this would result
									// in a non-unique solution, the cage is not
									// valid.
									if (DEBUG_GRID_GENERATOR_FULL) {
										Log
												.i(TAG,
														"         This cage type will result in a "
																+ "non-unique solution. The new cage "
																+ "contains values "
																+ duplicateValues
																		.toString()
																+ " in row "
																+ newCageRow
																+ " which are also used in row "
																+ row
																+ " within cage "
																+ otherCageId
																+ ".");
									}
									return true;
								}
							}
						}
					}
				}
			}
		}

		// No overlapping subset found
		return false;
	}

	/**
	 * Generates the operator to be used for the cage, semi-randomly.
	 * 
	 * @param cellValues
	 *            An array contain the correct value of each cell in the cage
	 */
	private CageOperator generateCageOperator(int[] cellValues) {
		// A cage consisting of one single cell has no operator.
		if (cellValues.length == 1) {
			return CageOperator.NONE;
		}

		// For cages of size 2 and bigger a weight (i.e. the chance on choosing
		// that operator) will be determined.
		int divisionWeight;
		int subtractionWeight;
		int addWeight;
		int multiplyWeight;

		if (cellValues.length == 2) {
			// A cage consisting of two cells can have any operator. Divide and
			// subtraction get extra weight because those operators are not used
			// in bigger cages.
			// As division can only be used if the remainder after division
			// equals 0 it gets even more weight.
			int lower = Math.min(cellValues[0], cellValues[1]);
			int higher = Math.max(cellValues[0], cellValues[1]);
			divisionWeight = ((higher % lower == 0) ? 50 : 0);
			subtractionWeight = 30;
			addWeight = 15;
			multiplyWeight = 15;
		} else {
			// Cage has three or more cells. Division and subtraction are not
			// allowed as operators.
			divisionWeight = 0;
			subtractionWeight = 0;
			addWeight = 50;
			multiplyWeight = 50;
		}

		// Determine a random number in the range of the total weight of the
		// operator available.
		int totalWeight = divisionWeight + subtractionWeight + addWeight
				+ multiplyWeight;
		double index = mRandom.nextInt(totalWeight);

		// Check whether the division operator has to be applied
		if (index < divisionWeight) {
			return CageOperator.DIVIDE;
		}
		index -= divisionWeight;

		// Check whether the subtraction operator has to be applied
		if (index < subtractionWeight) {
			return CageOperator.SUBTRACT;
		}
		index -= subtractionWeight;

		// Check whether the multiply operator has to be applied. If it value is
		// greater than the maximum cage result, the add operator will be used
		// instead.
		if (index < multiplyWeight) {
			int cageResultMultiplication = getCageResult(cellValues, CageOperator.MULTIPLY);
			if (cageResultMultiplication <= mGridGeneratingParameters.mMaxCageResult) {
				return CageOperator.MULTIPLY;
			}
			// Multiplication leads to a cage value that is too big to be
			// displayed on this device. Instead of multiplication the add
			// operator will be used for this cage which leads to a small cage
			// outcome.
			Log.i(TAG, "GameSeed: " + mGridGeneratingParameters.mGameSeed
					+ " cage result " + cageResultMultiplication + " is rejected");
		}

		// Use ADD in all other cases.
		return CageOperator.ADD;
	}

	private int getCageResult(int[] cellValues, CageOperator cageOperator) {
		if (cageOperator == CageOperator.NONE && cellValues.length == 1) {
			return cellValues[0];
		}

		if (cageOperator == CageOperator.DIVIDE && cellValues.length == 2) {
			int lower = Math.min(cellValues[0], cellValues[1]);
			int higher = Math.max(cellValues[0], cellValues[1]);
			return higher / lower;
		}

		if (cageOperator == CageOperator.SUBTRACT && cellValues.length == 2) {
			int lower = Math.min(cellValues[0], cellValues[1]);
			int higher = Math.max(cellValues[0], cellValues[1]);
			return higher - lower;
		}

		if (cageOperator == CageOperator.ADD && cellValues.length >= 2) {
			int total = 0;
			for (int cellValue : cellValues) {
				total += cellValue;
			}
			return total;
		}

		if ((cageOperator == CageOperator.MULTIPLY) && cellValues.length >= 2) {
			int total = 1;
			for (int cellValue : cellValues) {
				total *= cellValue;
			}
			return total;
		}

		// No valid result was calculated.
		return -1;
	}

	private void clearAllCages() {
		for (GridCell cell : this.mCells) {
			cell.clearCage();
		}
		this.mCages = new ArrayList<GridCage>();
	}

	/* Clear any cells containing the given number. */
	private void clearValue(int value) {
		for (GridCell cell : this.mCells)
			if (cell.getCorrectValue() == value)
				cell.setCorrectValue(0);
	}

	/* Determine if the given value is in the given column */
	private boolean valueInColumn(int column, int value) {
		for (int row = 0; row < mGridSize; row++)
			if (this.mCells.get(column + row * mGridSize).getCorrectValue() == value)
				return true;
		return false;
	}

	/* Fetch the cell at the given row, column */
	private GridCell getCellAt(int row, int column) {
		int cellId = getCellId(row, column);
		if (cellId < 0) {
			return null;
		}

		return mCells.get(cellId);
	}

	private int getCellId(int row, int column) {
		if (row < 0 || row >= mGridSize) {
			return -1;
		}
		if (column < 0 || column >= mGridSize) {
			return -1;
		}
		return (row * mGridSize) + column;
	}

	@Override
	protected void onCancelled(Void result) {
		if (mListener != null) {
			mListener.onCancelGridGenerating();
		}
		super.onCancelled(result);
	}
}
