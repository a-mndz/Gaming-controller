# Requirements Document

## Introduction

This specification defines two new features for the EuroPad Android application, an ETS2 (Euro Truck Simulator 2) controller app. The features address user feedback regarding limited customization and navigation options:

1. **Movable Icons / Customizable Layout**: Enables users to reposition controller buttons through a drag-and-drop interface in settings, with positions persisted across sessions.

2. **Back to Login from Settings**: Provides navigation from the settings panel (KeymapPanel) back to the login/connection screen (DeckPickerScreen), allowing users to switch servers or connection methods without returning to the controller cockpit first.

## Glossary

- **EuroPad**: The Android application that serves as a virtual controller for Euro Truck Simulator 2
- **DeckPickerScreen**: The login/connection screen where users select PC servers and configure connection methods (Wi-Fi or USB tethering)
- **Ets2Deck**: The main controller cockpit screen containing steering wheel, pedals, and utility buttons
- **KeymapPanel**: The settings panel accessible from the controller cockpit for key remapping and sensitivity configuration
- **DeckLayout**: A pure math object defining fixed fractional positions for all controller elements based on screen aspect ratio
- **DeckRect**: A data class representing a controller element's position with center coordinates (cx, cy) and dimensions (w, h) as fractions of canvas size
- **SharedPreferences**: Android's persistent key-value storage used for user preferences
- **Control_Element**: Any interactive UI component on the controller cockpit (buttons, wheel, pedals, gear selector)
- **Layout_Preset**: A saved configuration of control element positions that can be loaded or reset
- **Edit_Mode**: A state in which control elements can be repositioned via drag-and-drop gestures

## Requirements

### Requirement 1: Enter Layout Edit Mode

**User Story:** As a truck driver using EuroPad, I want to enter a layout editing mode from the settings panel, so that I can customize the position of controller elements.

#### Acceptance Criteria

1. WHEN the user is in the KeymapPanel, THE KeymapPanel SHALL display an "EDIT LAYOUT" option
2. WHEN the user selects "EDIT LAYOUT", THE System SHALL enter Edit_Mode and display all Control_Elements as draggable items
3. WHILE in Edit_Mode, THE System SHALL display visual indicators showing that elements can be moved
4. WHILE in Edit_Mode, THE System SHALL display options to save changes, reset to default, or cancel editing; IF these options fail to appear, THE System SHALL allow the user to exit Edit_Mode via alternative methods such as back button or menu navigation

### Requirement 2: Drag and Drop Control Elements

**User Story:** As a truck driver using EuroPad, I want to drag control elements to new positions on the screen, so that I can create a personalized controller layout.

#### Acceptance Criteria

1. WHILE in Edit_Mode, WHEN the user performs a drag gesture on a Control_Element, THE System SHALL move the Control_Element to follow the touch position
2. WHILE in Edit_Mode, THE System SHALL constrain Control_Element positions within the visible screen boundaries; IF a Control_Element is already positioned outside boundaries when Edit_Mode begins, THEN THE System SHALL leave the element in place and only constrain subsequent drag movements
3. WHILE in Edit_Mode, THE System SHALL prevent Control_Elements from overlapping beyond a defined minimum separation threshold
4. WHEN the user releases a dragged Control_Element, THE System SHALL snap the element to the nearest valid position

### Requirement 3: Persist Custom Layout

**User Story:** As a truck driver using EuroPad, I want my custom layout to be saved automatically, so that I don't have to reconfigure positions every time I use the app.

#### Acceptance Criteria

1. WHEN the user saves layout changes in Edit_Mode, THE System SHALL store all Control_Element positions in SharedPreferences
2. WHEN the user exits Edit_Mode without saving, THE System SHALL discard all temporary position changes
3. WHEN the user opens the Ets2Deck after previously saving a custom layout, THE System SHALL load and apply the saved Control_Element positions from SharedPreferences; IF the stored data is corrupted or inaccessible, THEN THE System SHALL silently fall back to default DeckLayout positions
4. WHEN the user selects "RESET TO DEFAULT" in Edit_Mode, THE System SHALL restore all Control_Element positions to the default DeckLayout values and clear stored custom positions; THE System SHALL NOT clear custom positions in any other scenario

### Requirement 4: Layout Storage Format

**User Story:** As a developer maintaining EuroPad, I want the layout positions stored in a structured format, so that the data is reliable and easy to parse.

#### Acceptance Criteria

1. WHEN the System saves a custom layout, THE System SHALL serialize Control_Element positions as fractional values (0.0 to 1.0) relative to canvas dimensions
2. WHEN the System loads a saved layout, THE System SHALL deserialize the fractional positions and apply them to the current canvas dimensions
3. FOR ALL saved layouts, THE System SHALL store the Control_Element identifier, center-x, center-y, width, and height values
4. WHEN the System parses a saved layout, IF any stored value is outside the valid range (0.0 to 1.0), THEN THE System SHALL clamp the value to the nearest valid boundary

### Requirement 5: Navigate Back to Login Screen

**User Story:** As a truck driver using EuroPad, I want to navigate from the settings panel back to the login screen, so that I can switch servers or connection methods without returning to the controller cockpit first.

#### Acceptance Criteria

1. WHEN the user is in the KeymapPanel, THE KeymapPanel SHALL display a "DISCONNECT" or "BACK TO LOGIN" option regardless of connection state
2. WHEN the user selects the "DISCONNECT" option, THE System SHALL navigate to the DeckPickerScreen
3. WHEN navigating to DeckPickerScreen from KeymapPanel, THE System SHALL preserve any previously entered connection settings and preferences
4. WHEN the user navigates to DeckPickerScreen from KeymapPanel, THE System SHALL close the current connection if one exists; IF no connection exists, THE System SHALL still attempt a close operation

### Requirement 6: Layout Editing Visual Feedback

**User Story:** As a truck driver using EuroPad, I want clear visual feedback while editing the layout, so that I understand what changes I'm making.

#### Acceptance Criteria

1. WHILE in Edit_Mode, THE System SHALL highlight the currently selected Control_Element with a distinct visual style
2. WHILE dragging a Control_Element, THE System SHALL display a semi-transparent preview of the element at its new position
3. WHILE in Edit_Mode, THE System SHALL display the element label for each Control_Element
4. WHEN two Control_Elements would overlap after a move, THE System SHALL display a warning indicator

### Requirement 7: Supported Control Elements

**User Story:** As a truck driver using EuroPad, I want to reposition all controller elements, so that I have full control over my cockpit layout.

#### Acceptance Criteria

1. THE System SHALL support repositioning of the following Control_Elements: LIGHTS button, WIPER button, VIPER (windshield washer) button, HANDBRAKE button, SETTINGS button, MENU button, CAMERA button, GEAR selector (R/N/D), left/right turn signal arrows, steering wheel (in touch mode), ACCELERATOR pedal, and BRAKE pedal; WHERE the steering mode is gyroscope, touch-mode pedals SHALL remain independently repositionable
2. WHERE the steering mode is set to gyroscope AND gyro pedals are configured to be independent, THE System SHALL support repositioning of the gyro-specific ACCELERATOR and BRAKE pedals independently from touch-mode pedals; WHERE gyro pedals are not configured as independent, THE System SHALL NOT support repositioning of gyro-specific pedals
3. WHEN the user switches between wheel mode and gyro mode, THE System SHALL maintain separate custom layouts for each mode if they have been customized

### Requirement 8: Non-Functional - Performance

**User Story:** As a truck driver using EuroPad, I want the layout customization to be responsive, so that editing feels smooth and natural.

#### Acceptance Criteria

1. WHILE dragging a Control_Element, THE System SHALL update the element position within 16 milliseconds of touch movement
2. WHEN loading a saved custom layout, THE System SHALL apply all Control_Element positions within 100 milliseconds
3. WHEN saving a custom layout, THE System SHALL complete the save operation within 50 milliseconds

### Requirement 9: Non-Functional - Usability

**User Story:** As a truck driver using EuroPad, I want the layout editing interface to be intuitive, so that I can customize my controller without reading documentation.

#### Acceptance Criteria

1. WHEN the user enters Edit_Mode for the first time, THE System SHALL display a brief instruction indicating how to move and save elements
2. WHILE in Edit_Mode, THE System SHALL maintain a minimum touch target size of 44 density-independent pixels for all interactive elements including actionable controls, decorative elements, dividers, and labels
3. WHEN the user attempts to exit Edit_Mode with unsaved changes, THE System SHALL prompt the user to save, discard, or cancel; IF the prompt fails to appear, THE System SHALL prevent the exit until the prompt is displayed or changes are handled
