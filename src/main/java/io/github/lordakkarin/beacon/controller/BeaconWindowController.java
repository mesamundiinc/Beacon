/*
 * Copyright 2016 Johannes Donath <johannesd@torchmind.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.lordakkarin.beacon.controller;

import com.google.inject.Inject;
import io.github.lordakkarin.beacon.control.NumberField;
import io.github.lordakkarin.beacon.control.cell.NetworkInterfaceCell;
import io.github.lordakkarin.beacon.control.cell.ServiceCell;
import io.github.lordakkarin.beacon.upnp.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.annotation.Nonnull;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * <strong>Beacon Window Controller</strong>
 *
 * Provides a controller for the main application window.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
public class BeaconWindowController implements Initializable {
        private static final PseudoClass ERROR_PSEUDO_CLASS = PseudoClass.getPseudoClass("error");
        @FXML
        private NumberField customPortField;
        @FXML
        private ComboBox<ProtocolType> customProtocolSelector;
        @FXML
        private ComboBox<NetworkInterface> networkInterfaceSelector;
        @FXML
        private ListView<Service> presetSelector;
        @FXML
        private VBox root;
        private final ServiceManager serviceManager;
        @FXML
        private Button startButton;
        @FXML
        private Button stopButton;

        @Inject
        public BeaconWindowController(@Nonnull ServiceManager serviceManager) {
                this.serviceManager = serviceManager;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void initialize(@Nonnull URL location, @Nonnull ResourceBundle resources) {
                // bind properties to ensure their correct updating
                this.startButton.managedProperty().bind(this.startButton.visibleProperty());
                this.stopButton.managedProperty().bind(this.stopButton.visibleProperty());

                // set component contents
                this.networkInterfaceSelector.setItems(this.serviceManager.getInterfaceList());
                this.presetSelector.setItems(FXCollections.observableArrayList(Arrays.asList(StockService.values())));
                this.customProtocolSelector.setItems(FXCollections.observableArrayList(Arrays.asList(ProtocolType.values())));

                // register all custom cell factories
                this.networkInterfaceSelector.setButtonCell(new NetworkInterfaceCell());
                this.networkInterfaceSelector.setCellFactory((p) -> new NetworkInterfaceCell());
                this.presetSelector.setCellFactory((p) -> new ServiceCell());

                // execute initial selection for the user
                this.networkInterfaceSelector.getSelectionModel().select(0);
                this.networkInterfaceSelector.getItems().addListener((ListChangeListener<NetworkInterface>) c -> this.networkInterfaceSelector.getSelectionModel().select(0));

                // hook network interface
                this.networkInterfaceSelector.valueProperty().addListener((ob, o, n) -> {
                        // remove error class
                        this.networkInterfaceSelector.pseudoClassStateChanged(ERROR_PSEUDO_CLASS, false);
                });

                // hook properties on custom service fields
                this.customProtocolSelector.valueProperty().addListener((ob, o, n) -> {
                        // remove error class
                        this.customProtocolSelector.pseudoClassStateChanged(ERROR_PSEUDO_CLASS, false);

                        // un-select the currently active preset if the port differs
                        Service service = this.presetSelector.getSelectionModel().getSelectedItem();

                        if (service != null && service.getType() != n) {
                                this.presetSelector.getSelectionModel().clearSelection();
                        }
                });

                this.customPortField.textProperty().addListener((ob, o, n) -> {
                        // remove the error class
                        this.customPortField.pseudoClassStateChanged(ERROR_PSEUDO_CLASS, false);

                        // un-select the currently active preset if the port differs
                        Service service = this.presetSelector.getSelectionModel().getSelectedItem();

                        if (service != null && service.getPort() != Integer.parseUnsignedInt(n)) {
                                this.presetSelector.getSelectionModel().clearSelection();
                        }
                });
        }

        /**
         * Handles the refreshing of our cached network interface list.
         *
         * @param event the source event.
         */
        @FXML
        private void onNetworkInterfaceRefresh(@Nonnull ActionEvent event) {
                this.serviceManager.refreshInterfaceList();
        }

        /**
         * Handles the selection of presets.
         *
         * @param event the source event.
         */
        @FXML
        private void onPresetSelect(@Nonnull MouseEvent event) {
                Service service = this.presetSelector.getSelectionModel().getSelectedItem();

                if (service != null) {
                        this.customProtocolSelector.getSelectionModel().select(service.getType());
                        this.customPortField.setText(Integer.toString(service.getPort()));
                }
        }

        /**
         * Handles the port opening requested by a user.
         *
         * @param event the source event.
         */
        @FXML
        private void onStart(@Nonnull ActionEvent event) {
                NetworkInterface selectedInterface = this.networkInterfaceSelector.getValue();

                if (selectedInterface == null) {
                        this.networkInterfaceSelector.pseudoClassStateChanged(ERROR_PSEUDO_CLASS, true);
                        return;
                }

                Optional<InetAddress> address = this.serviceManager.findCompatibleAddress(selectedInterface);

                if (!address.isPresent()) {
                        this.networkInterfaceSelector.pseudoClassStateChanged(ERROR_PSEUDO_CLASS, true);
                        return;
                }

                String portRaw = this.customPortField.getText();

                if (portRaw == null || portRaw.isEmpty()) {
                        this.customPortField.pseudoClassStateChanged(ERROR_PSEUDO_CLASS, true);
                }

                ProtocolType protocolType = this.customProtocolSelector.getValue();

                if (protocolType == null) {
                        this.customProtocolSelector.pseudoClassStateChanged(ERROR_PSEUDO_CLASS, true);
                }

                if (protocolType == null || portRaw == null || portRaw.isEmpty()) {
                        return;
                }

                this.serviceManager.publishService(address.get(), new CustomService(protocolType, Integer.parseUnsignedInt(portRaw)));
                this.startButton.setVisible(false);
                this.stopButton.setVisible(true);
        }

        /**
         * Handles port closing.
         *
         * @param event the source event.
         */
        @FXML
        private void onStop(@Nonnull ActionEvent event) {
                this.serviceManager.shutdown();
                this.stopButton.setVisible(false);
                this.startButton.setVisible(true);
        }

        /**
         * Displays the about page of this application.
         *
         * @param event the source event.
         */
        @FXML
        private void onTitleBarAbout(@Nonnull ActionEvent event) {
                // TODO
        }

        /**
         * Closes the application window.
         *
         * @param event the source event.
         */
        @FXML
        private void onTitleBarClose(@Nonnull ActionEvent event) {
                Platform.exit();
        }

        /**
         * Iconifies (minimizes) the application window.
         *
         * @param event the source event.
         */
        @FXML
        private void onTitleBarIconify(@Nonnull ActionEvent event) {
                ((Stage) this.root.getScene().getWindow()).setIconified(true);
        }
}