package com.apppn.apppn.ServiceImpl;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.apppn.apppn.DTO.Request.InventoryDTO;
import com.apppn.apppn.DTO.Request.PagoDTO;
import com.apppn.apppn.Exceptions.ErrorResponse;
import com.apppn.apppn.Exceptions.SuccessException;
import com.apppn.apppn.Models.Archivos;
import com.apppn.apppn.Models.Compra;
import com.apppn.apppn.Models.Inventory;
import com.apppn.apppn.Models.Pago;
import com.apppn.apppn.Models.ProductoCompra;
import com.apppn.apppn.Models.User;
import com.apppn.apppn.Repository.CompraRepository;
import com.apppn.apppn.Repository.InventoryRepository;
import com.apppn.apppn.Repository.PagoRepository;
import com.apppn.apppn.Security.Security.JwtUtils;
import com.apppn.apppn.Service.ArchivoService;
import com.apppn.apppn.Service.CompraService;
import com.apppn.apppn.Service.InventoryService;
import com.apppn.apppn.Service.PagoService;
import com.apppn.apppn.Service.UserService;
import com.apppn.apppn.Utils.Functions;

@Service
public class PagoServiceImpl implements PagoService {

    private final PagoRepository pagoRepository;
    private final ArchivoService archivosService;
    private final CompraService compraService;
    private final InventoryService inventoryService;
    private final Functions functions;
    private final HttpServletRequest request;
    private final JwtUtils jwtUtils;
    private final UserService userService;
    private final CompraRepository compraRepository;
    private final InventoryRepository inventoryRepository;

    public PagoServiceImpl(PagoRepository pagoRepository, ArchivoService archivosService, CompraService compraService,
            InventoryService inventoryService, Functions functions, HttpServletRequest request, JwtUtils jwtUtils,
            UserService userService, CompraRepository compraRepository, InventoryRepository inventoryRepository) {
        this.pagoRepository = pagoRepository;
        this.archivosService = archivosService;
        this.compraService = compraService;
        this.inventoryService = inventoryService;
        this.functions = functions;
        this.request = request;
        this.jwtUtils = jwtUtils;
        this.userService = userService;
        this.compraRepository = compraRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public ResponseEntity<?> crearPago(Long idCompra, PagoDTO pagoDTO) {
        ResponseEntity<Object> response = compraService.obtenerCompra(idCompra);
        if (!response.getStatusCode().equals(HttpStatus.OK)) {
            return response;
        }

        Compra compra = (Compra) response.getBody();
        if (Objects.isNull(compra)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("El compra no existe"));
        }

        Pago pago = new Pago();
        pago.setTotalPago(pagoDTO.getTotalPago());

        ResponseEntity<?> responseArchivos = archivosService.saveFile(pagoDTO.getArchivo(), "/data/uploads/");
        if (!responseArchivos.getStatusCode().equals(HttpStatus.OK)) {
            return responseArchivos;
        }
        Archivos archivos = (Archivos) responseArchivos.getBody();

        pago.setArchivos(archivos);
        compra.setPago(pago);
        compra.setIsPago(true);
        try {
            pago.setFechaPago(functions.obtenerFechaYhora());
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        String token = request.getAttribute("token").toString();

        String username = jwtUtils.extractUsername(token);
        if (Objects.isNull(username)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Token no valido"));
        }

        ResponseEntity<?> responseUser = userService.getUserByEmail(username);
        if (!responseUser.getStatusCode().equals(HttpStatus.OK)) {
            return responseUser;
        }

        User user = (User) responseUser.getBody();
        if (Objects.isNull(user)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("El usuario no existe"));
        }
        pago.setUser(user);

        ResponseEntity<?> compraResponse = compraService.guardarCompraBD(compra);
        if (!compraResponse.getStatusCode().equals(HttpStatus.OK)) {
            return compraResponse;
        }

        // LIBERAR A INVENTARIO
        InventoryDTO inventoryDTO = new InventoryDTO();
        inventoryDTO.setTotalInventoryValue(0.0);
        inventoryDTO.setQuantity(0);
        try {
            inventoryDTO.setDateInventory(functions.obtenerFechaYhora());
        } catch (ParseException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("ERROR AL PARSEAR LA FECHA Y HORA"));
        }

        for (ProductoCompra pro : compra.getProductoCompras()) {
            inventoryDTO.setQuantity(inventoryDTO.getQuantity() + pro.getCantidad());
            Double valorPro = (pro.getCosto() + pro.getFlete()) * pro.getCantidad();
            inventoryDTO.setTotalInventoryValue(inventoryDTO.getTotalInventoryValue() + valorPro);
        }

        inventoryDTO.setProductos(compra.getProductoCompras());

        ResponseEntity<?> inventoryResponse = inventoryService.saveInventory(inventoryDTO);
        if (!inventoryResponse.getStatusCode().equals(HttpStatus.OK)) {
            return inventoryResponse;
        }

        Inventory inventory = (Inventory) inventoryResponse.getBody();
        if (Objects.isNull(inventory)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("El inventario no existe"));
        }

        return ResponseEntity.status(HttpStatus.OK).body(pago);
    }

    @Override
    public ResponseEntity<?> obtenerPagos() {
        List<Pago> pagos = pagoRepository.findAll();
        if (Objects.isNull(pagos)) {
            pagos = new ArrayList<>();
        }
        return ResponseEntity.status(HttpStatus.OK).body(pagos);
    }

    @Override
    public ResponseEntity<?> eliminarPago(Long idPago, Long idInventario) {
        Pago pago = pagoRepository.findById(idPago).orElse(null);
        if (Objects.isNull(pago)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("Pago no encontrado"));
        }

        Compra compra = compraRepository.findByPago(pago);
        if (Objects.isNull(compra)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("No existe compra con ese pago"));
        }
        compra.setPago(null);
        compra = compraRepository.save(compra);

        if (Objects.nonNull(idInventario)) {
            Inventory inventory = inventoryRepository.findById(idInventario).orElse(null);
            if (Objects.isNull(inventory)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("El inventario no existe"));
            }
            inventoryRepository.delete(inventory);

        }

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessException("El pago ha sido eliminado"));

    }

    @Override
    public ResponseEntity<?> obtenerPago(Date fechaPago) {

        LocalDate fecha = fechaPago.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDateTime fechaHora = LocalDateTime.of(fecha, LocalTime.MIN);
        LocalDateTime fechaHoraFinal = LocalDateTime.of(fecha, LocalTime.MAX);

        Date fechaPagoInicio = Date.from(fechaHora.atZone(ZoneId.systemDefault()).toInstant());
        Date fechaPagoFinal = Date.from(fechaHoraFinal.atZone(ZoneId.systemDefault()).toInstant());

        List<Pago> pagos = pagoRepository.findByfechaPagoBetween(fechaPagoInicio, fechaPagoFinal);

        if (CollectionUtils.isEmpty(pagos)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pago no encontrado");
        }
        return ResponseEntity.status(HttpStatus.OK).body(pagos);
    }
}
